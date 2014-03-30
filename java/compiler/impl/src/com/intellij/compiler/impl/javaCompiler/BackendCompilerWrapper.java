/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.compiler.*;
import com.intellij.compiler.classParsing.AnnotationConstantValue;
import com.intellij.compiler.classParsing.MethodInfo;
import com.intellij.compiler.impl.CompileDriver;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.make.Cache;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.compiler.make.DependencyCache;
import com.intellij.compiler.make.MakeUtil;
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.util.Chunk;
import com.intellij.util.cls.ClsFormatException;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.asm4.ClassWriter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author Eugene Zhuravlev
 * @since Jan 24, 2003
 */
public class BackendCompilerWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.BackendCompilerWrapper");

  private final BackendCompiler myCompiler;

  private final CompileContextEx myCompileContext;
  private final List<VirtualFile> myFilesToCompile;
  private final TranslatingCompiler.OutputSink mySink;
  private final Chunk<Module> myChunk;
  private final Project myProject;
  private final Map<Module, VirtualFile> myModuleToTempDirMap = new THashMap<Module, VirtualFile>();
  private final ProjectFileIndex myProjectFileIndex;
  @NonNls private static final String PACKAGE_ANNOTATION_FILE_NAME = "package-info.java";
  private static final FileObject myStopThreadToken = new FileObject(new File(""), new byte[0]);
  public final Map<String, Set<CompiledClass>> myFileNameToSourceMap=  new THashMap<String, Set<CompiledClass>>();
  private final Set<VirtualFile> myProcessedPackageInfos = new HashSet<VirtualFile>();
  private final CompileStatistics myStatistics;
  private volatile String myModuleName = null;
  private boolean myForceCompileTestsSeparately = false;

  public BackendCompilerWrapper(Chunk<Module> chunk, @NotNull final Project project,
                                @NotNull List<VirtualFile> filesToCompile,
                                @NotNull CompileContextEx compileContext,
                                @NotNull BackendCompiler compiler, TranslatingCompiler.OutputSink sink) {
    myChunk = chunk;
    myProject = project;
    myCompiler = compiler;
    myCompileContext = compileContext;
    myFilesToCompile = filesToCompile;
    mySink = sink;
    myProjectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    CompileStatistics stat = compileContext.getUserData(CompileStatistics.KEY);
    if (stat == null) {
      stat = new CompileStatistics();
      compileContext.putUserData(CompileStatistics.KEY, stat);
    }
    myStatistics = stat;
  }

  public void compile() throws CompilerException, CacheCorruptedException {
    Application application = ApplicationManager.getApplication();
    try {
      if (!myFilesToCompile.isEmpty()) {
        if (application.isUnitTestMode()) {
          saveTestData();
        }
        compileModules(buildModuleToFilesMap(myFilesToCompile));
      }
    }
    catch (SecurityException e) {
      throw new CompilerException(CompilerBundle.message("error.compiler.process.not.started", e.getMessage()), e);
    }
    catch (IllegalArgumentException e) {
      throw new CompilerException(e.getMessage(), e);
    }
    finally {
      for (final VirtualFile file : myModuleToTempDirMap.values()) {
        if (file != null) {
          final File ioFile = new File(file.getPath());
          FileUtil.asyncDelete(ioFile);
        }
      }
      myModuleToTempDirMap.clear();
    }

    if (!myFilesToCompile.isEmpty() && myCompileContext.getMessageCount(CompilerMessageCategory.ERROR) == 0) {
      // package-info.java hack
      final List<TranslatingCompiler.OutputItem> outputs = new ArrayList<TranslatingCompiler.OutputItem>();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          for (final VirtualFile file : myFilesToCompile) {
            if (PACKAGE_ANNOTATION_FILE_NAME.equals(file.getName()) && !myProcessedPackageInfos.contains(file)) {
              outputs.add(new OutputItemImpl(file));
            }
          }
        }
      });
      if (!outputs.isEmpty()) {
        mySink.add(null, outputs, VirtualFile.EMPTY_ARRAY);
      }
    }
  }

  public boolean isForceCompileTestsSeparately() {
    return myForceCompileTestsSeparately;
  }

  public void setForceCompileTestsSeparately(boolean forceCompileTestsSeparately) {
    myForceCompileTestsSeparately = forceCompileTestsSeparately;
  }

  private Map<Module, List<VirtualFile>> buildModuleToFilesMap(final List<VirtualFile> filesToCompile) {
    if (myChunk.getNodes().size() == 1) {
      return Collections.singletonMap(myChunk.getNodes().iterator().next(), Collections.unmodifiableList(filesToCompile));
    }
    return CompilerUtil.buildModuleToFilesMap(myCompileContext, filesToCompile);
  }

  private void compileModules(final Map<Module, List<VirtualFile>> moduleToFilesMap) throws CompilerException {
    try {
      compileChunk(new ModuleChunk(myCompileContext, myChunk, moduleToFilesMap));
    }
    catch (IOException e) {
      throw new CompilerException(e.getMessage(), e);
    }
  }

  private void compileChunk(ModuleChunk chunk) throws IOException {
    final String chunkPresentableName = getPresentableNameFor(chunk);
    myModuleName = chunkPresentableName;

    // validate encodings
    if (chunk.getModuleCount() > 1) {
      validateEncoding(chunk, chunkPresentableName);
      // todo: validation for bytecode target?
    }

    runTransformingCompilers(chunk);


    final List<OutputDir> outs = new ArrayList<OutputDir>();
    File fileToDelete = getOutputDirsToCompileTo(chunk, outs);

    try {
      for (final OutputDir outputDir : outs) {
        chunk.setSourcesFilter(outputDir.getKind());
        doCompile(chunk, outputDir.getPath());
      }
    }
    finally {
      if (fileToDelete != null) {
        FileUtil.asyncDelete(fileToDelete);
      }
    }
  }

  private void validateEncoding(ModuleChunk chunk, String chunkPresentableName) {
    final CompilerEncodingService es = CompilerEncodingService.getInstance(myProject);
    Charset charset = null;
    for (Module module : chunk.getModules()) {
      final Charset moduleCharset = es.getPreferredModuleEncoding(module);
      if (charset == null) {
        charset = moduleCharset;
      }
      else {
        if (!Comparing.equal(charset, moduleCharset)) {
          // warn user
          final Charset chunkEncoding = CompilerEncodingService.getPreferredModuleEncoding(chunk);
          final StringBuilder message = new StringBuilder();
          message.append("Modules in chunk [");
          message.append(chunkPresentableName);
          message.append("] configured to use different encodings.\n");
          if (chunkEncoding != null) {
            message.append("\"").append(chunkEncoding.name()).append("\" encoding will be used to compile the chunk");
          }
          else {
            message.append("Default compiler encoding will be used to compile the chunk");
          }
          myCompileContext.addMessage(CompilerMessageCategory.INFORMATION, message.toString(), null, -1, -1);
          break;
        }
      }
    }
  }


  private static String getPresentableNameFor(final ModuleChunk chunk) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        final Module[] modules = chunk.getModules();
        StringBuilder moduleName = new StringBuilder(Math.min(128, modules.length * 8));
        for (int idx = 0; idx < modules.length; idx++) {
          final Module module = modules[idx];
          if (idx > 0) {
            moduleName.append(", ");
          }
          moduleName.append(module.getName());
          if (moduleName.length() > 128 && idx + 1 < modules.length /*name is already too long and seems to grow longer*/) {
            moduleName.append("...");
            break;
          }
        }
        return moduleName.toString();
      }
    });
  }

  @Nullable
  private File getOutputDirsToCompileTo(ModuleChunk chunk, final List<OutputDir> dirs) throws IOException {
    File fileToDelete = null;
    if (chunk.getModuleCount() == 1) { // optimization
      final Module module = chunk.getModules()[0];
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          final String sourcesOutputDir = getOutputDir(module);
          if (shouldCompileTestsSeparately(module)) {
            if (sourcesOutputDir != null) {
              dirs.add(new OutputDir(sourcesOutputDir, ModuleChunk.SOURCES));
            }
            final String testsOutputDir = getTestsOutputDir(module);
            if (testsOutputDir == null) {
              LOG.error("Tests output dir is null for module \"" + module.getName() + "\"");
            }
            else {
              dirs.add(new OutputDir(testsOutputDir, ModuleChunk.TEST_SOURCES));
            }
          }
          else { // both sources and test sources go into the same output
            if (sourcesOutputDir == null) {
              LOG.error("Sources output dir is null for module \"" + module.getName() + "\"");
            }
            else {
              dirs.add(new OutputDir(sourcesOutputDir, ModuleChunk.ALL_SOURCES));
            }
          }
        }
      });
    }
    else { // chunk has several modules
      final File outputDir = FileUtil.createTempDirectory("compile", "output");
      fileToDelete = outputDir;
      dirs.add(new OutputDir(outputDir.getPath(), ModuleChunk.ALL_SOURCES));
    }
    return fileToDelete;
  }


  private boolean shouldCompileTestsSeparately(Module module) {
    if (myForceCompileTestsSeparately) {
      return true;
    }
    final String moduleTestOutputDirectory = getTestsOutputDir(module);
    if (moduleTestOutputDirectory == null) {
      return false;
    }
    // here we have test output specified
    final String moduleOutputDirectory = getOutputDir(module);
    if (moduleOutputDirectory == null) {
      // only test output is specified, so should return true
      return true;
    }
    return !FileUtil.pathsEqual(moduleTestOutputDirectory, moduleOutputDirectory);
  }

  private void saveTestData() {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (VirtualFile file : myFilesToCompile) {
          CompilerManagerImpl.addCompiledPath(file.getPath());
        }
      }
    });
  }

  private final Object lock = new Object();

  private class SynchedCompilerParsing extends CompilerParsingThread {
    private final ClassParsingThread myClassParsingThread;

    private SynchedCompilerParsing(Process process,
                                  final CompileContext context,
                                  OutputParser outputParser,
                                  ClassParsingThread classParsingThread,
                                  boolean readErrorStream,
                                  boolean trimLines) {
      super(process, outputParser, readErrorStream, trimLines,context);
      myClassParsingThread = classParsingThread;
    }

    public void setProgressText(String text) {
      synchronized (lock) {
        super.setProgressText(text);
      }
    }

    public void message(CompilerMessageCategory category, String message, String url, int lineNum, int columnNum) {
      synchronized (lock) {
        super.message(category, message, url, lineNum, columnNum);
      }
    }

    public void fileProcessed(String path) {
      synchronized (lock) {
        sourceFileProcessed();
      }
    }

    protected void processCompiledClass(final FileObject classFileToProcess) throws CacheCorruptedException {
      synchronized (lock) {
        myClassParsingThread.addPath(classFileToProcess);
      }
    }
  }

  private void doCompile(@NotNull final ModuleChunk chunk, @NotNull String outputDir) throws IOException {
    myCompileContext.getProgressIndicator().checkCanceled();

    if (ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return chunk.getFilesToCompile().isEmpty() ? Boolean.TRUE : Boolean.FALSE;
      }
    }).booleanValue()) {
      return; // should not invoke javac with empty sources list
    }

    ModuleType moduleType = ModuleType.get(chunk.getModules()[0]);
    if ((chunk.getJdk() == null || !(chunk.getJdk().getSdkType() instanceof JavaSdkType)) &&
        !(moduleType instanceof JavaModuleType || moduleType.createModuleBuilder() instanceof JavaModuleBuilder)) {
      // TODO
      // don't try to compile non-java type module
      return;
    }

    int exitValue = 0;
    try {
      final Process process = myCompiler.launchProcess(chunk, outputDir, myCompileContext);
      final long compilationStart = System.currentTimeMillis();
      final ClassParsingThread classParsingThread = new ClassParsingThread(isJdk6(chunk.getJdk()), outputDir);
      final Future<?> classParsingThreadFuture = ApplicationManager.getApplication().executeOnPooledThread(classParsingThread);

      OutputParser errorParser = myCompiler.createErrorParser(outputDir, process);
      CompilerParsingThread errorParsingThread = errorParser == null
                                                 ? null
                                                 : new SynchedCompilerParsing(process, myCompileContext, errorParser, classParsingThread,
                                                                              true, errorParser.isTrimLines());
      Future<?> errorParsingThreadFuture = null;
      if (errorParsingThread != null) {
        errorParsingThreadFuture = ApplicationManager.getApplication().executeOnPooledThread(errorParsingThread);
      }

      OutputParser outputParser = myCompiler.createOutputParser(outputDir);
      CompilerParsingThread outputParsingThread = outputParser == null
                                                  ? null
                                                  : new SynchedCompilerParsing(process, myCompileContext, outputParser, classParsingThread,
                                                                               false, outputParser.isTrimLines());
      Future<?> outputParsingThreadFuture = null;
      if (outputParsingThread != null) {
        outputParsingThreadFuture = ApplicationManager.getApplication().executeOnPooledThread(outputParsingThread);
      }

      try {
        exitValue = process.waitFor();
      }
      catch (InterruptedException e) {
        process.destroy();
        exitValue = process.exitValue();
      }
      catch (Error e) {
        process.destroy();
        exitValue = process.exitValue();
        throw e;
      }
      finally {
        if (CompileDriver.ourDebugMode) {
          System.out.println("Compiler exit code is " + exitValue);
        }
        if (errorParsingThread != null) {
          errorParsingThread.setProcessTerminated(true);
        }
        if (outputParsingThread != null) {
          outputParsingThread.setProcessTerminated(true);
        }
        joinThread(errorParsingThreadFuture);
        joinThread(outputParsingThreadFuture);
        classParsingThread.stopParsing();
        joinThread(classParsingThreadFuture);

        registerParsingException(outputParsingThread);
        registerParsingException(errorParsingThread);
        assert outputParsingThread == null || !outputParsingThread.processing;
        assert errorParsingThread == null || !errorParsingThread.processing;
        assert classParsingThread == null || !classParsingThread.processing;
      }
    }
    finally {
      compileFinished(exitValue, chunk, outputDir);
      myModuleName = null;
    }
  }

  private static void joinThread(final Future<?> threadFuture) {
    if (threadFuture != null) {
      try {
        threadFuture.get();
      }
      catch (InterruptedException ignored) {
        LOG.info("Thread interrupted", ignored);
      }
      catch (ExecutionException ignored) {
        LOG.info("Thread interrupted", ignored);
      }
    }
  }

  private void registerParsingException(final CompilerParsingThread outputParsingThread) {
    Throwable error = outputParsingThread == null ? null : outputParsingThread.getError();
    if (error != null) {
      String message = error.getMessage();
      if (error instanceof CacheCorruptedException) {
        myCompileContext.requestRebuildNextTime(message);
      }
      else {
        myCompileContext.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
      }
    }
  }

  private void runTransformingCompilers(final ModuleChunk chunk) {
    final JavaSourceTransformingCompiler[] transformers =
      CompilerManager.getInstance(myProject).getCompilers(JavaSourceTransformingCompiler.class);
    if (transformers.length == 0) {
      return;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Running transforming compilers...");
    }
    final Module[] modules = chunk.getModules();
    for (final JavaSourceTransformingCompiler transformer : transformers) {
      final Map<VirtualFile, VirtualFile> originalToCopyFileMap = new HashMap<VirtualFile, VirtualFile>();
      final Application application = ApplicationManager.getApplication();
      application.invokeAndWait(new Runnable() {
        public void run() {
          for (final Module module : modules) {
            for (final VirtualFile file : chunk.getFilesToCompile(module)) {
              final VirtualFile untransformed = chunk.getOriginalFile(file);
              if (transformer.isTransformable(untransformed)) {
                application.runWriteAction(new Runnable() {
                  public void run() {
                    try {
                      // if untransformed != file, the file is already a (possibly transformed) copy of the original 'untransformed' file.
                      // If this is the case, just use already created copy and do not copy file content once again
                      final VirtualFile fileCopy = untransformed.equals(file)? createFileCopy(getTempDir(module), file) : file;
                      originalToCopyFileMap.put(file, fileCopy);
                    }
                    catch (IOException e) {
                      // skip it
                    }
                  }
                });
              }
            }
          }
        }
      }, myCompileContext.getProgressIndicator().getModalityState());

      // do actual transform
      for (final Module module : modules) {
        final List<VirtualFile> filesToCompile = chunk.getFilesToCompile(module);
        for (int j = 0; j < filesToCompile.size(); j++) {
          final VirtualFile file = filesToCompile.get(j);
          final VirtualFile fileCopy = originalToCopyFileMap.get(file);
          if (fileCopy != null) {
            final boolean ok = transformer.transform(myCompileContext, fileCopy, chunk.getOriginalFile(file));
            if (ok) {
              chunk.substituteWithTransformedVersion(module, j, fileCopy);
            }
          }
        }
      }
    }
  }

  private VirtualFile createFileCopy(VirtualFile tempDir, final VirtualFile file) throws IOException {
    final String fileName = file.getName();
    if (tempDir.findChild(fileName) != null) {
      int idx = 0;
      while (true) {
        //noinspection HardCodedStringLiteral
        final String dirName = "dir" + idx++;
        final VirtualFile dir = tempDir.findChild(dirName);
        if (dir == null) {
          tempDir = tempDir.createChildDirectory(this, dirName);
          break;
        }
        if (dir.findChild(fileName) == null) {
          tempDir = dir;
          break;
        }
      }
    }
    return VfsUtilCore.copyFile(this, file, tempDir);
  }

  private VirtualFile getTempDir(Module module) throws IOException {
    VirtualFile tempDir = myModuleToTempDirMap.get(module);
    if (tempDir == null) {
      final String projectName = myProject.getName();
      final String moduleName = module.getName();
      File tempDirectory = FileUtil.createTempDirectory(projectName, moduleName);
      tempDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDirectory);
      if (tempDir == null) {
        LOG.error("Cannot locate temp directory " + tempDirectory.getPath());
      }
      myModuleToTempDirMap.put(module, tempDir);
    }
    return tempDir;
  }

  private void compileFinished(int exitValue, final ModuleChunk chunk, final String outputDir) {
    if (exitValue != 0 && !myCompileContext.getProgressIndicator().isCanceled() && myCompileContext.getMessageCount(CompilerMessageCategory.ERROR) == 0) {
      myCompileContext.addMessage(CompilerMessageCategory.ERROR, CompilerBundle.message("error.compiler.internal.error", exitValue), null, -1, -1);
    }

    myCompiler.compileFinished();
    final List<File> toRefresh = new ArrayList<File>();
    final Map<String, Collection<TranslatingCompiler.OutputItem>> results = new HashMap<String, Collection<TranslatingCompiler.OutputItem>>();
    try {
      final FileTypeManager typeManager = FileTypeManager.getInstance();
      final String outputDirPath = outputDir.replace(File.separatorChar, '/');
      try {
        for (final Module module : chunk.getModules()) {
          for (final VirtualFile root : chunk.getSourceRoots(module)) {
            final String packagePrefix = myProjectFileIndex.getPackageNameByDirectory(root);
            if (LOG.isDebugEnabled()) {
              LOG.debug("Building output items for " + root.getPresentableUrl() + "; output dir = " + outputDirPath + "; packagePrefix = \"" + packagePrefix + "\"");
            }
            buildOutputItemsList(outputDirPath, module, root, typeManager, root, packagePrefix, toRefresh, results);
          }
        }
      }
      catch (CacheCorruptedException e) {
        myCompileContext.requestRebuildNextTime(CompilerBundle.message("error.compiler.caches.corrupted"));
        if (LOG.isDebugEnabled()) {
          LOG.debug(e);
        }
      }
    }
    finally {
      CompilerUtil.refreshIOFiles(toRefresh);
      for (Iterator<Map.Entry<String, Collection<TranslatingCompiler.OutputItem>>> it = results.entrySet().iterator(); it.hasNext();) {
        Map.Entry<String, Collection<TranslatingCompiler.OutputItem>> entry = it.next();
        mySink.add(entry.getKey(), entry.getValue(), VirtualFile.EMPTY_ARRAY);
        it.remove(); // to free memory
      }
    }
    myFileNameToSourceMap.clear(); // clear the map before the next use
  }

  private void buildOutputItemsList(final String outputDir, final Module module, VirtualFile from,
                                    final FileTypeManager typeManager,
                                    final VirtualFile sourceRoot,
                                    final String packagePrefix, final List<File> filesToRefresh, final Map<String, Collection<TranslatingCompiler.OutputItem>> results) throws CacheCorruptedException {
    final Ref<CacheCorruptedException> exRef = new Ref<CacheCorruptedException>(null);
    final ModuleFileIndex fileIndex = ModuleRootManager.getInstance(module).getFileIndex();
    final GlobalSearchScope srcRootScope = GlobalSearchScope.moduleScope(module).intersectWith(
        GlobalSearchScopes.directoryScope(myProject, sourceRoot, true));
    
    final ContentIterator contentIterator = new ContentIterator() {
      public boolean processFile(final VirtualFile child) {
        try {
          if (child.isValid()) {
            if (!child.isDirectory() && myCompiler.getCompilableFileTypes().contains(child.getFileType())) {
              updateOutputItemsList(outputDir, child, sourceRoot, packagePrefix, filesToRefresh, results, srcRootScope);
            }
          }
          return true;
        }
        catch (CacheCorruptedException e) {
          exRef.set(e);
          return false;
        }
      }
    };
    if (fileIndex.isInContent(from)) {
      // use file index for iteration to handle 'inner modules' and excludes properly
      fileIndex.iterateContentUnderDirectory(from, contentIterator);
    }
    else {
      // seems to be a root for generated sources
      VfsUtilCore.visitChildrenRecursively(from, new VirtualFileVisitor() {
        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          if (!file.isDirectory()) {
            contentIterator.processFile(file);
          }
          return true;
        }
      });
    }
    final CacheCorruptedException exc = exRef.get();
    if (exc != null) {
      throw exc;
    }
  }

  private void putName(String sourceFileName, int classQName, String relativePathToSource, String pathToClass) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Registering [sourceFileName, relativePathToSource, pathToClass] = [" + sourceFileName + "; " + relativePathToSource +
                "; " + pathToClass + "]");
    }
    Set<CompiledClass> paths = myFileNameToSourceMap.get(sourceFileName);

    if (paths == null) {
      paths = new HashSet<CompiledClass>();
      myFileNameToSourceMap.put(sourceFileName, paths);
    }
    paths.add(new CompiledClass(classQName, relativePathToSource, pathToClass));
  }

  private void updateOutputItemsList(final String outputDir, final VirtualFile srcFile,
                                     VirtualFile sourceRoot,
                                     final String packagePrefix, final List<File> filesToRefresh,
                                     Map<String, Collection<TranslatingCompiler.OutputItem>> results,
                                     final GlobalSearchScope srcRootScope) throws CacheCorruptedException {
    final Cache newCache = myCompileContext.getDependencyCache().getNewClassesCache();
    final Set<CompiledClass> paths = myFileNameToSourceMap.get(srcFile.getName());
    if (paths == null || paths.isEmpty()) {
      return;
    }
    final String filePath = "/" + calcPackagePath(srcFile, sourceRoot, packagePrefix);
    for (final CompiledClass cc : paths) {
      myCompileContext.getProgressIndicator().checkCanceled();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Checking [pathToClass; relPathToSource] = " + cc);
      }
      
      boolean pathsEquals = FileUtil.pathsEqual(filePath, cc.relativePathToSource);
      if (!pathsEquals) {
        final String qName = myCompileContext.getDependencyCache().resolve(cc.qName);
        if (qName != null) {
          pathsEquals = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
            public Boolean compute() {
              final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
              PsiClass psiClass = facade.findClass(qName, srcRootScope);
              if (psiClass == null) {
                final int dollarIndex = qName.indexOf("$");
                if (dollarIndex >= 0) {
                  final String topLevelClassName = qName.substring(0, dollarIndex);
                  psiClass = facade.findClass(topLevelClassName, srcRootScope);
                }
              }
              if (psiClass != null) {
                final VirtualFile vFile = psiClass.getContainingFile().getVirtualFile();
                return vFile != null && vFile.equals(srcFile);
              }
              return false;
            }
          });
        }
      }
      
      if (pathsEquals) {
        final String outputPath = cc.pathToClass.replace(File.separatorChar, '/');
        final Pair<String, String> realLocation = moveToRealLocation(outputDir, outputPath, srcFile, filesToRefresh);
        if (realLocation != null) {
          Collection<TranslatingCompiler.OutputItem> outputs = results.get(realLocation.getFirst());
          if (outputs == null) {
            outputs = new ArrayList<TranslatingCompiler.OutputItem>();
            results.put(realLocation.getFirst(), outputs);
          }
          outputs.add(new OutputItemImpl(realLocation.getSecond(), srcFile));
          if (PACKAGE_ANNOTATION_FILE_NAME.equals(srcFile.getName())) {
            myProcessedPackageInfos.add(srcFile);
          }
          if (CompilerConfiguration.MAKE_ENABLED) {
            newCache.setPath(cc.qName, realLocation.getSecond());
          }
          if (LOG.isDebugEnabled()) {
            LOG.debug("Added output item: [outputDir; outputPath; sourceFile]  = [" + realLocation.getFirst() + "; " +
                      realLocation.getSecond() + "; " + srcFile.getPresentableUrl() + "]");
          }
        }
        else {
          myCompileContext.addMessage(CompilerMessageCategory.ERROR, "Failed to copy from temporary location to output directory: " + outputPath + " (see idea.log for details)", null, -1, -1);
          if (LOG.isDebugEnabled()) {
            LOG.debug("Failed to move to real location: " + outputPath + "; from " + outputDir);
          }
        }
      }
    }
  }

  /**
   *
   * @param srcFile
   * @param sourceRoot
   * @param packagePrefix
   * @return A 'package'-path to a given src file relative to a specified root. "/" slashes must be used
   */
  protected static String calcPackagePath(VirtualFile srcFile, VirtualFile sourceRoot, String packagePrefix) {
    final String prefix = packagePrefix != null && packagePrefix.length() > 0 ? packagePrefix.replace('.', '/') + "/" : "";
    return prefix + VfsUtilCore.getRelativePath(srcFile, sourceRoot, '/');
  }

  @Nullable
  private Pair<String, String> moveToRealLocation(String tempOutputDir, String pathToClass, VirtualFile sourceFile, final List<File> filesToRefresh) {
    final Module module = myCompileContext.getModuleByFile(sourceFile);
    if (module == null) {
      final String message =
        "Cannot determine module for source file: " + sourceFile.getPresentableUrl() + ";\nCorresponding output file: " + pathToClass;
      LOG.info(message);
      myCompileContext.addMessage(CompilerMessageCategory.WARNING, message, sourceFile.getUrl(), -1, -1);
      // do not move: looks like source file has been invalidated, need recompilation
      return new Pair<String, String>(tempOutputDir, pathToClass);
    }
    final String realOutputDir;
    if (myCompileContext.isInTestSourceContent(sourceFile)) {
      realOutputDir = getTestsOutputDir(module);
      LOG.assertTrue(realOutputDir != null);
    }
    else {
      realOutputDir = getOutputDir(module);
      LOG.assertTrue(realOutputDir != null);
    }

    if (FileUtil.pathsEqual(tempOutputDir, realOutputDir)) { // no need to move
      filesToRefresh.add(new File(pathToClass));
      return new Pair<String, String>(realOutputDir, pathToClass);
    }

    final String realPathToClass = realOutputDir + pathToClass.substring(tempOutputDir.length());
    final File fromFile = new File(pathToClass);
    final File toFile = new File(realPathToClass);

    boolean success = fromFile.renameTo(toFile);
    if (!success) {
      // assuming cause of the fail: intermediate dirs do not exist
      FileUtil.createParentDirs(toFile);
      // retry after making non-existent dirs
      success = fromFile.renameTo(toFile);
    }
    if (!success) { // failed to move the file: e.g. because source and destination reside on different mountpoints.
      try {
        FileUtil.copy(fromFile, toFile);
        FileUtil.delete(fromFile);
        success = true;
      }
      catch (IOException e) {
        LOG.info(e);
        success = false;
      }
    }
    if (success) {
      filesToRefresh.add(toFile);
      return new Pair<String, String>(realOutputDir, realPathToClass);
    }
    return null;
  }

  private final Map<Module, String> myModuleToTestsOutput = new HashMap<Module, String>();

  private String getTestsOutputDir(final Module module) {
    if (myModuleToTestsOutput.containsKey(module)) {
      return myModuleToTestsOutput.get(module);
    }
    final VirtualFile outputDirectory = myCompileContext.getModuleOutputDirectoryForTests(module);
    final String out = outputDirectory != null? outputDirectory.getPath() : null;
    myModuleToTestsOutput.put(module, out);
    return out;
  }

  private final Map<Module, String> myModuleToOutput = new HashMap<Module, String>();

  private String getOutputDir(final Module module) {
    if (myModuleToOutput.containsKey(module)) {
      return myModuleToOutput.get(module);
    }
    final VirtualFile outputDirectory = myCompileContext.getModuleOutputDirectory(module);
    final String out = outputDirectory != null? outputDirectory.getPath() : null;
    myModuleToOutput.put(module, out);
    return out;
  }

  private void sourceFileProcessed() {
    myStatistics.incFilesCount();
    updateStatistics();
  }

  private void updateStatistics() {
    final String msg;
    String moduleName = myModuleName;
    if (moduleName != null) {
      msg = CompilerBundle.message("statistics.files.classes.module", myStatistics.getFilesCount(), myStatistics.getClassesCount(), moduleName);
    }
    else {
      msg = CompilerBundle.message("statistics.files.classes", myStatistics.getFilesCount(), myStatistics.getClassesCount());
    }
    myCompileContext.getProgressIndicator().setText2(msg);
    //myCompileContext.getProgressIndicator().setFraction(1.0* myProcessedFilesCount /myTotalFilesToCompile);
  }

  private class ClassParsingThread implements Runnable {
    private final BlockingQueue<FileObject> myPaths = new ArrayBlockingQueue<FileObject>(50000);
    private CacheCorruptedException myError = null;
    private final boolean myAddNotNullAssertions;
    private final boolean myIsJdk16;
    private final String myOutputDir;

    private ClassParsingThread(final boolean isJdk16, String outputDir) {
      myIsJdk16 = isJdk16;
      myOutputDir = FileUtil.toSystemIndependentName(outputDir);
      myAddNotNullAssertions = CompilerConfiguration.getInstance(myProject).isAddNotNullAssertions();
    }

    private volatile boolean processing;
    public void run() {
      processing = true;
      try {
        while (true) {
          FileObject path = myPaths.take();

          if (path == myStopThreadToken) {
            break;
          }
          processPath(path, myProject);
        }
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (CacheCorruptedException e) {
        myError = e;
      }
      finally {
        processing = false;
      }
    }

    public void addPath(FileObject path) throws CacheCorruptedException {
      if (myError != null) {
        throw myError;
      }
      myPaths.offer(path);
    }

    public void stopParsing() {
      myPaths.offer(myStopThreadToken);
    }

    private void processPath(FileObject fileObject, Project project) throws CacheCorruptedException {
      File file = fileObject.getFile();
      final String path = file.getPath();
      try {
        if (CompilerConfiguration.MAKE_ENABLED) {
          byte[] fileContent = fileObject.getContent();
          // the file is assumed to exist!
          final DependencyCache dependencyCache = myCompileContext.getDependencyCache();
          final int newClassQName = dependencyCache.reparseClassFile(file, fileContent);
          final Cache newClassesCache = dependencyCache.getNewClassesCache();
          final String sourceFileName = newClassesCache.getSourceFileName(newClassQName);
          final String qName = dependencyCache.resolve(newClassQName);
          String relativePathToSource = "/" + MakeUtil.createRelativePathToSource(qName, sourceFileName);
          putName(sourceFileName, newClassQName, relativePathToSource, path);
          boolean haveToInstrument = myAddNotNullAssertions && hasNotNullAnnotations(newClassesCache, dependencyCache.getSymbolTable(), newClassQName, project);

          if (haveToInstrument) {
            try {
              ClassReader reader = new ClassReader(fileContent, 0, fileContent.length);
              ClassWriter writer = new PsiClassWriter(myProject, myIsJdk16);

              if (NotNullVerifyingInstrumenter.processClassFile(reader, writer)) {
                fileObject = new FileObject(file, writer.toByteArray());
              }
            }
            catch (Exception ignored) {
              LOG.info(ignored);
            }
          }

          fileObject.save();
        }
        else {
          final String _path = FileUtil.toSystemIndependentName(path);
          final int dollarIndex = _path.indexOf('$');
          final int tailIndex = dollarIndex >=0 ? dollarIndex : _path.length() - ".class".length();
          final int slashIndex = _path.lastIndexOf('/');
          final String sourceFileName = _path.substring(slashIndex + 1, tailIndex) + ".java";
          String relativePathToSource = _path.substring(myOutputDir.length(), tailIndex) + ".java";
          putName(sourceFileName, 0 /*doesn't matter here*/ , relativePathToSource.startsWith("/")? relativePathToSource : "/" + relativePathToSource, path);
        }
      }
      catch (ClsFormatException e) {
        final String m = e.getMessage();
        String message = CompilerBundle.message("error.bad.class.file.format", StringUtil.isEmpty(m) ? path : m + "\n" + path);
        myCompileContext.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
        LOG.info(e);
      }
      catch (IOException e) {
        myCompileContext.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
        LOG.info(e);
      }
      finally {
        myStatistics.incClassesCount();
        updateStatistics();
      }
    }
  }

  private static boolean hasNotNullAnnotations(final Cache cache, final SymbolTable symbolTable, final int className, Project project) throws CacheCorruptedException {
    final NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    final List<String> notNulls = manager.getNotNulls();
    for (MethodInfo methodId : cache.getMethods(className)) {
      for (AnnotationConstantValue annotation : methodId.getRuntimeInvisibleAnnotations()) {
        if (notNulls.contains(symbolTable.getSymbol(annotation.getAnnotationQName()))) {
          return true;
        }
      }
      final AnnotationConstantValue[][] paramAnnotations = methodId.getRuntimeInvisibleParameterAnnotations();
      for (AnnotationConstantValue[] _singleParamAnnotations : paramAnnotations) {
        for (AnnotationConstantValue annotation : _singleParamAnnotations) {
          if (notNulls.contains(symbolTable.getSymbol(annotation.getAnnotationQName()))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean isJdk6(final Sdk jdk) {
    return jdk != null && JavaSdk.getInstance().isOfVersionOrHigher(jdk, JavaSdkVersion.JDK_1_6);
  }
  
  private static final class CompileStatistics {
    private static final Key<CompileStatistics> KEY = Key.create("_Compile_Statistics_");
    private int myClassesCount;
    private int myFilesCount;

    public int getClassesCount() {
      return myClassesCount;
    }

    public int incClassesCount() {
      return ++myClassesCount;
    }

    public int getFilesCount() {
      return myFilesCount;
    }

    public int incFilesCount() {
      return ++myFilesCount;
    }
  }
}
