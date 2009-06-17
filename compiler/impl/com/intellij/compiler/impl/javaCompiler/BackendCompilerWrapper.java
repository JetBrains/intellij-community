/*
 * @author: Eugene Zhuravlev
 * Date: Jan 24, 2003
 * Time: 4:25:47 PM
 */
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.compiler.*;
import com.intellij.compiler.classParsing.AnnotationConstantValue;
import com.intellij.compiler.classParsing.MethodInfo;
import com.intellij.compiler.impl.CompileDriver;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.make.*;
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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Chunk;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.containers.ByteTrie;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public class BackendCompilerWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.BackendCompilerWrapper");

  private final BackendCompiler myCompiler;
  private final Set<VirtualFile> mySuccesfullyCompiledJavaFiles; // VirtualFile
  private final List<TranslatingCompiler.OutputItem> myOutputItems;
  private final ByteTrie myTrie = new ByteTrie(); // to store OutputItems' paths more compactly

  private final CompileContextEx myCompileContext;
  private final List<VirtualFile> myFilesToCompile;
  private final Project myProject;
  private final Set<VirtualFile> myFilesToRecompile;
  private final Map<Module, VirtualFile> myModuleToTempDirMap = new THashMap<Module, VirtualFile>();
  private final ProjectFileIndex myProjectFileIndex;
  @NonNls private static final String PACKAGE_ANNOTATION_FILE_NAME = "package-info.java";
  private static final FileObject myStopThreadToken = new FileObject(null,null);

  public BackendCompilerWrapper(@NotNull final Project project,
                                @NotNull List<VirtualFile> filesToCompile,
                                @NotNull CompileContextEx compileContext,
                                @NotNull BackendCompiler compiler) {
    myProject = project;
    myCompiler = compiler;
    myCompileContext = compileContext;
    myFilesToCompile = filesToCompile;
    myFilesToRecompile = new HashSet<VirtualFile>(filesToCompile);
    myProjectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    mySuccesfullyCompiledJavaFiles = new HashSet<VirtualFile>(filesToCompile.size());
    myOutputItems = new ArrayList<TranslatingCompiler.OutputItem>(filesToCompile.size());
  }

  public List<TranslatingCompiler.OutputItem> compile() throws CompilerException, CacheCorruptedException {
    Application application = ApplicationManager.getApplication();
    final Set<VirtualFile> allDependent = new HashSet<VirtualFile>();
    COMPILE:
    try {
      if (!myFilesToCompile.isEmpty()) {
        if (application.isUnitTestMode()) {
          saveTestData();
        }

        final Map<Module, List<VirtualFile>> moduleToFilesMap = buildModuleToFilesMap(myCompileContext, myFilesToCompile);
        compileModules(moduleToFilesMap);
      }

      Collection<VirtualFile> dependentFiles;
      do {
        dependentFiles = findDependentFiles();

        if (!dependentFiles.isEmpty()) {
          myFilesToRecompile.addAll(dependentFiles);
          allDependent.addAll(dependentFiles);
          if (myCompileContext.getProgressIndicator().isCanceled() || myCompileContext.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
            break COMPILE;
          }
          final List<VirtualFile> filesInScope = getFilesInScope(dependentFiles);
          if (filesInScope.isEmpty()) {
            break;
          }
          final Map<Module, List<VirtualFile>> moduleToFilesMap = buildModuleToFilesMap(myCompileContext, filesInScope);
          myCompileContext.getDependencyCache().clearTraverseRoots();
          compileModules(moduleToFilesMap);
        }
      }
      while (!dependentFiles.isEmpty() && myCompileContext.getMessageCount(CompilerMessageCategory.ERROR) == 0);
    }
    catch (SecurityException e) {
      throw new CompilerException(CompilerBundle.message("error.compiler.process.not.started", e.getMessage()), e);
    }
    catch (IllegalArgumentException e) {
      throw new CompilerException(e.getMessage(), e);
    }
    finally {
      CompileDriver.runInContext(myCompileContext, CompilerBundle.message("progress.deleting.temp.files"), new ThrowableRunnable<RuntimeException>(){
        public void run() throws RuntimeException {
          for (final VirtualFile file : myModuleToTempDirMap.values()) {
            if (file != null) {
              final File ioFile = new File(file.getPath());
              FileUtil.asyncDelete(ioFile);
            }
          }
          myModuleToTempDirMap.clear();
        }
      });
    }

    if (myCompileContext.getProgressIndicator().isCanceled()) {
      myFilesToRecompile.clear();
      // when cancelled pretend nothing was compiled and next compile will compile everything from the scratch
      return Collections.emptyList();
    }

    // do not update caches if cancelled because there is a chance that they will be incomplete
    CompileDriver.runInContext(myCompileContext, CompilerBundle.message("progress.updating.caches"), new ThrowableRunnable<CacheCorruptedException>(){
      public void run() throws CacheCorruptedException {
        ProgressIndicator indicator = myCompileContext.getProgressIndicator();
        myCompileContext.getDependencyCache().update(indicator);
      }
    });

    myFilesToRecompile.removeAll(mySuccesfullyCompiledJavaFiles);
    if (myCompileContext.getMessageCount(CompilerMessageCategory.ERROR) != 0) {
      myFilesToRecompile.addAll(allDependent);
    }
    processPackageInfoFiles();
    return myOutputItems;
  }

  // package-info.java hack
  private void processPackageInfoFiles() {
    if (myFilesToRecompile.isEmpty()) {
      return;
    }
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final List<VirtualFile> packageInfoFiles = new ArrayList<VirtualFile>(myFilesToRecompile.size());
        for (final VirtualFile file : myFilesToRecompile) {
          if (PACKAGE_ANNOTATION_FILE_NAME.equals(file.getName())) {
            packageInfoFiles.add(file);
          }
        }
        if (!packageInfoFiles.isEmpty()) {
          final Set<VirtualFile> badFiles = getFilesCompiledWithErrors();
          for (final VirtualFile packageInfoFile : packageInfoFiles) {
            if (!badFiles.contains(packageInfoFile)) {
              myOutputItems.add(new OutputItemImpl(packageInfoFile));
              myFilesToRecompile.remove(packageInfoFile);
            }
          }
        }
      }
    });
  }

  private List<VirtualFile> getFilesInScope(final Collection<VirtualFile> dependentFiles) {
    final List<VirtualFile> filesInScope = new ArrayList<VirtualFile>(dependentFiles.size());
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (VirtualFile dependentFile : dependentFiles) {
          if (myCompileContext.getCompileScope().belongs(dependentFile.getUrl())) {
            filesInScope.add(dependentFile);
          }
        }
      }
    });
    return filesInScope;
  }

  private void compileModules(final Map<Module, List<VirtualFile>> moduleToFilesMap) throws CompilerException {
    final List<ModuleChunk> chunks = getModuleChunks(moduleToFilesMap);
    List<VirtualFile> files = ContainerUtil.concat(moduleToFilesMap.values());
    LOG.debug("------ Make started. Files to compile: " + files.size() + " " + StringUtil.first(files.toString(), 500, true));
    myProcessedFilesCount = 0;
    myTotalFilesToCompile = files.size();

    long start = System.currentTimeMillis();
    for (final ModuleChunk chunk : chunks) {
      try {
        boolean success = compileChunk(chunk);
        if (!success) return;
      }
      catch (IOException e) {
        throw new CompilerException(e.getMessage(), e);
      }
    }

    long end = System.currentTimeMillis();
    String time = (end - start)/1000/60 + "m" + ((end - start) % 60000)/1000 + "s";
    BackendCompiler compiler = ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject)).getDefaultCompiler();
    LOG.debug("Compilation took " + time + "; for " + compiler.getPresentableName() + ". ");
  }

  private boolean compileChunk(ModuleChunk chunk) throws IOException {
    runTransformingCompilers(chunk);

    setPresentableNameFor(chunk);

    final List<OutputDir> outs = new ArrayList<OutputDir>();
    File fileToDelete = getOutputDirsToCompileTo(chunk, outs);

    try {
      for (final OutputDir outputDir : outs) {
        doCompile(chunk, outputDir.getPath(), outputDir.getKind());
        if (myCompileContext.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
          return false;
        }
      }
    }
    finally {
      if (fileToDelete != null) {
        FileUtil.asyncDelete(fileToDelete);
      }
    }

    return true;
  }


  private void setPresentableNameFor(final ModuleChunk chunk) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
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
        myModuleName = moduleName.toString();
      }
    });
  }

  private File getOutputDirsToCompileTo(ModuleChunk chunk, final List<OutputDir> pairs) throws IOException {
    File fileToDelete = null;
    if (chunk.getModuleCount() == 1) { // optimization
      final Module module = chunk.getModules()[0];
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          final String sourcesOutputDir = getOutputDir(module);
          if (shouldCompileTestsSeparately(module)) {
            if (sourcesOutputDir != null) {
              pairs.add(new OutputDir(sourcesOutputDir, ModuleChunk.SOURCES));
            }
            final String testsOutputDir = getTestsOutputDir(module);
            if (testsOutputDir == null) {
              LOG.error("Tests output dir is null for module \"" + module.getName() + "\"");
            }
            else {
              pairs.add(new OutputDir(testsOutputDir, ModuleChunk.TEST_SOURCES));
            }
          }
          else { // both sources and test sources go into the same output
            if (sourcesOutputDir == null) {
              LOG.error("Sources output dir is null for module \"" + module.getName() + "\"");
            }
            else {
              pairs.add(new OutputDir(sourcesOutputDir, ModuleChunk.ALL_SOURCES));
            }
          }
        }
      });
    }
    else { // chunk has several modules
      final File outputDir = FileUtil.createTempDirectory("compile", "output");
      fileToDelete = outputDir;
      pairs.add(new OutputDir(outputDir.getPath(), ModuleChunk.ALL_SOURCES));
    }
    return fileToDelete;
  }

  private List<ModuleChunk> getModuleChunks(final Map<Module, List<VirtualFile>> moduleToFilesMap) {
    final List<Module> modules = new ArrayList<Module>(moduleToFilesMap.keySet());
    final List<Chunk<Module>> chunks = ApplicationManager.getApplication().runReadAction(new Computable<List<Chunk<Module>>>() {
      public List<Chunk<Module>> compute() {
        return ModuleCompilerUtil.getSortedModuleChunks(myProject, modules);
      }
    });
    final List<ModuleChunk> moduleChunks = new ArrayList<ModuleChunk>(chunks.size());
    for (final Chunk<Module> chunk : chunks) {
      moduleChunks.add(new ModuleChunk(myCompileContext, chunk, moduleToFilesMap));
    }
    return moduleChunks;
  }

  private boolean shouldCompileTestsSeparately(Module module) {
    final String moduleTestOutputDirectory = getTestsOutputDir(module);
    if (moduleTestOutputDirectory == null) {
      return false;
    }
    final String moduleOutputDirectory = getOutputDir(module);
    return !FileUtil.pathsEqual(moduleTestOutputDirectory, moduleOutputDirectory);
  }

  public Collection<VirtualFile> getFilesToRecompile() {
    return myFilesToRecompile;
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

  private final TIntHashSet myProcessedNames = new TIntHashSet();
  private final Set<VirtualFile> myProcessedFiles = new HashSet<VirtualFile>();

  private Collection<VirtualFile> findDependentFiles() throws CacheCorruptedException {
    myCompileContext.getProgressIndicator().setText(CompilerBundle.message("progress.checking.dependencies"));

    final DependencyCache dependencyCache = myCompileContext.getDependencyCache();
    final Pair<int[], Set<VirtualFile>> deps =
        dependencyCache.findDependentClasses(myCompileContext, myProject, mySuccesfullyCompiledJavaFiles, myCompiler.getDependencyProcessor());

    final TIntHashSet currentDeps = new TIntHashSet(deps.getFirst());
    currentDeps.removeAll(myProcessedNames.toArray());
    final int[] depQNames = currentDeps.toArray();
    myProcessedNames.addAll(deps.getFirst());

    final Set<VirtualFile> depFiles = new HashSet<VirtualFile>(deps.getSecond());
    depFiles.removeAll(myProcessedFiles);
    myProcessedFiles.addAll(deps.getSecond());

    final Set<VirtualFile> dependentFiles = new HashSet<VirtualFile>();
    final CacheCorruptedException[] _ex = {null};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(myProject);
          SourceFileFinder sourceFileFinder = new SourceFileFinder(myProject, myCompileContext);
          final Cache cache = dependencyCache.getCache();
          for (final int infoQName : depQNames) {
            final String qualifiedName = dependencyCache.resolve(infoQName);
            final VirtualFile file = sourceFileFinder.findSourceFile(qualifiedName, cache.getSourceFileName(infoQName));
            if (file != null) {
              if (!compilerConfiguration.isExcludedFromCompilation(file)) {
                dependentFiles.add(file);
                if (ApplicationManager.getApplication().isUnitTestMode()) {
                  CompilerManagerImpl.addRecompiledPath(file.getPath());
                }
              }
            }
            else {
              if (LOG.isDebugEnabled()) {
                LOG.debug("No source file for " + dependencyCache.resolve(infoQName) + " found");
              }
            }
          }
          for (final VirtualFile file : depFiles) {
            if (!compilerConfiguration.isExcludedFromCompilation(file)) {
              dependentFiles.add(file);
              if (ApplicationManager.getApplication().isUnitTestMode()) {
                CompilerManagerImpl.addRecompiledPath(file.getPath());
              }
            }
          }
        }
        catch (CacheCorruptedException e) {
          _ex[0] = e;
        }
      }
    });
    if (_ex[0] != null) {
      throw _ex[0];
    }
    myCompileContext.getProgressIndicator().setText(CompilerBundle.message("progress.found.dependent.files", dependentFiles.size()));

    return dependentFiles;
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

  private void doCompile(@NotNull final ModuleChunk chunk, @NotNull String outputDir, int sourcesFilter) throws IOException {
    myCompileContext.getProgressIndicator().checkCanceled();
    chunk.setSourcesFilter(sourcesFilter);

    if (ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return chunk.getFilesToCompile().isEmpty() ? Boolean.TRUE : Boolean.FALSE;
      }
    }).booleanValue()) {
      return; // should not invoke javac with empty sources list
    }

    ModuleType moduleType = chunk.getModules()[0].getModuleType();
    if (!(chunk.getJdk().getSdkType() instanceof JavaSdkType) &&
        !(moduleType instanceof JavaModuleType || moduleType.createModuleBuilder() instanceof JavaModuleBuilder)) {
      // TODO
      // don't try to compile non-java type module
      return;
    }

    int exitValue = 0;
    try {
      Process process = myCompiler.launchProcess(chunk, outputDir, myCompileContext);
      final ClassParsingThread classParsingThread = new ClassParsingThread(chunk,isJdk6(chunk.getJdk()));
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
      finally {
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
      }
      catch(ExecutionException ignored) {
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
            List<VirtualFile> filesToCompile = chunk.getFilesToCompile(module);
            for (final VirtualFile file : filesToCompile) {
              if (transformer.isTransformable(file)) {
                application.runWriteAction(new Runnable() {
                  public void run() {
                    try {
                      VirtualFile fileCopy = createFileCopy(getTempDir(module), file);
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
          VirtualFile fileCopy = originalToCopyFileMap.get(file);
          if (fileCopy != null) {
            final boolean ok = transformer.transform(myCompileContext, fileCopy, file);
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
    return VfsUtil.copyFile(this, file, tempDir);
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
    if (exitValue != 0 && !myCompileContext.getProgressIndicator().isCanceled() &&
        myCompileContext.getMessageCount(CompilerMessageCategory.ERROR) == 0) {
      myCompileContext.addMessage(CompilerMessageCategory.ERROR, CompilerBundle.message("error.compiler.internal.error", exitValue), null, -1, -1);
    }
    myCompiler.compileFinished();
    final VirtualFile[] sourceRoots = chunk.getSourceRoots();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final Set<VirtualFile> compiledWithErrors = getFilesCompiledWithErrors();
        final FileTypeManager typeManager = FileTypeManager.getInstance();
        final String outputDirPath = outputDir.replace(File.separatorChar, '/');
        if (LOG.isDebugEnabled()) {
          LOG.debug("myFileNameToSourceMap contains entries: " + chunk.myFileNameToSourceMap.size());
        }
        try {
          for (final VirtualFile root : sourceRoots) {
            final String packagePrefix = myProjectFileIndex.getPackageNameByDirectory(root);
            if (LOG.isDebugEnabled()) {
              LOG.debug("Building output items for " + root.getPresentableUrl() + "; output dir = " + outputDirPath + "; packagePrefix = \"" +
                        packagePrefix + "\"");
            }
            buildOutputItemsList(chunk, outputDirPath, root, typeManager, compiledWithErrors, root, packagePrefix);
          }
        }
        catch (CacheCorruptedException e) {
          myCompileContext.requestRebuildNextTime(CompilerBundle.message("error.compiler.caches.corrupted"));
          if (LOG.isDebugEnabled()) {
            LOG.debug(e);
          }
        }
      }
    });
    CompilerUtil.refreshIOFilesInterruptibly(myCompileContext, chunk.myFilesToRefresh, "Refreshing moved files");
    chunk.myFileNameToSourceMap.clear(); // clear the map before the next use
    chunk.myFilesToRefresh.clear();
  }

  private Set<VirtualFile> getFilesCompiledWithErrors() {
    CompilerMessage[] messages = myCompileContext.getMessages(CompilerMessageCategory.ERROR);
    Set<VirtualFile> compiledWithErrors = Collections.emptySet();
    if (messages.length > 0) {
      compiledWithErrors = new HashSet<VirtualFile>(messages.length);
      for (CompilerMessage message : messages) {
        final VirtualFile file = message.getVirtualFile();
        if (file != null) {
          compiledWithErrors.add(file);
        }
      }
    }
    return compiledWithErrors;
  }

  private void buildOutputItemsList(@NotNull final ModuleChunk chunk, final String outputDir,
                                    VirtualFile from,
                                    final FileTypeManager typeManager,
                                    final Set<VirtualFile> compiledWithErrors,
                                    final VirtualFile sourceRoot,
                                    final String packagePrefix) throws CacheCorruptedException {
    final Ref<CacheCorruptedException> exRef = new Ref<CacheCorruptedException>(null);
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    final ContentIterator contentIterator = new ContentIterator() {
      public boolean processFile(final VirtualFile child) {
        try {
          assert child.isValid();
          if (!child.isDirectory() && myCompiler.getCompilableFileTypes().contains(typeManager.getFileTypeByFile(child))) {
            updateOutputItemsList(chunk, outputDir, child, compiledWithErrors, sourceRoot, packagePrefix);
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
      new Object() {
        void iterateContent(VirtualFile from) {
          for (VirtualFile child : from.getChildren()) {
            if (child.isDirectory()) {
              iterateContent(child);
            }
            else {
              contentIterator.processFile(child);
            }
          }
        }
      }.iterateContent(from);
    }
    if (exRef.get() != null) {
      throw exRef.get();
    }
  }

  private static void putName(ModuleChunk chunk, String sourceFileName, int classQName, String relativePathToSource, String pathToClass) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Registering [sourceFileName, relativePathToSource, pathToClass] = [" + sourceFileName + "; " + relativePathToSource +
                "; " + pathToClass + "]");
    }
    Set<CompiledClass> paths = chunk.myFileNameToSourceMap.get(sourceFileName);

    if (paths == null) {
      paths = new HashSet<CompiledClass>();
      chunk.myFileNameToSourceMap.put(sourceFileName, paths);
    }
    paths.add(new CompiledClass(classQName, relativePathToSource, pathToClass));
  }

  private void updateOutputItemsList(@NotNull ModuleChunk chunk, final String outputDir, VirtualFile srcFile, Set<VirtualFile> compiledWithErrors,
                                     VirtualFile sourceRoot, final String packagePrefix) throws CacheCorruptedException {
    final Cache newCache = myCompileContext.getDependencyCache().getNewClassesCache();
    final Set<CompiledClass> paths = chunk.myFileNameToSourceMap.get(srcFile.getName());
    if (paths == null || paths.isEmpty()) {
      return;
    }
    final String prefix = packagePrefix != null && packagePrefix.length() > 0 ? packagePrefix.replace('.', '/') + "/" : "";
    final String filePath = "/" + prefix + VfsUtil.getRelativePath(srcFile, sourceRoot, '/');
    for (final CompiledClass cc : paths) {
      myCompileContext.getProgressIndicator().checkCanceled();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Checking [pathToClass; relPathToSource] = " + cc);
      }
      if (FileUtil.pathsEqual(filePath, cc.relativePathToSource)) {
        final String outputPath = cc.pathToClass.replace(File.separatorChar, '/');
        final Pair<String, String> realLocation = moveToRealLocation(chunk, outputDir, outputPath, srcFile);
        if (realLocation != null) {
          final String realOutputDir = realLocation.getFirst();
          final String relativeOutputPath = new String(realLocation.getSecond().substring(realLocation.first.length() + 1));
          myOutputItems.add(new OutputItemImpl(myTrie, realOutputDir, relativeOutputPath, srcFile));
          newCache.setPath(cc.qName, realLocation.getSecond());
          try {
            myCompileContext.updateZippedOuput(realOutputDir, relativeOutputPath);
          }
          catch (IOException e) {
            LOG.info(e); // todo
          }
          if (LOG.isDebugEnabled()) {
            LOG.debug("Added output item: [outputDir; outputPath; sourceFile]  = [" + realLocation.getFirst() + "; " +
                      realLocation.getSecond() + "; " + srcFile.getPresentableUrl() + "]");
          }
          if (!compiledWithErrors.contains(srcFile)) {
            mySuccesfullyCompiledJavaFiles.add(srcFile);
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

  private Pair<String, String> moveToRealLocation(ModuleChunk chunk, String tempOutputDir, String pathToClass, VirtualFile sourceFile) {
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
    }
    else {
      realOutputDir = getOutputDir(module);
    }

    if (FileUtil.pathsEqual(tempOutputDir, realOutputDir)) { // no need to move
      chunk.myFilesToRefresh.add(new File(pathToClass));
      return new Pair<String, String>(realOutputDir, pathToClass);
    }

    final String realPathToClass = realOutputDir + pathToClass.substring(tempOutputDir.length());
    final File fromFile = new File(pathToClass);
    final File toFile = new File(realPathToClass);

    boolean success = fromFile.renameTo(toFile);
    if (!success) {
      // assuming cause of the fail: intermediate dirs do not exist
      final File parentFile = toFile.getParentFile();
      if (parentFile != null) {
        parentFile.mkdirs();
        success = fromFile.renameTo(toFile); // retry after making non-existent dirs
      }
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
      chunk.myFilesToRefresh.add(toFile);
      return new Pair<String, String>(realOutputDir, realPathToClass);
    }
    return null;
  }

  private final Map<Module, String> myModuleToTestsOutput = new HashMap<Module, String>();

  private String getTestsOutputDir(final Module module) {
      if (myModuleToTestsOutput.containsKey(module)) {
        return myModuleToTestsOutput.get(module);
      }
      final String out = CompilerPaths.getModuleOutputPath(module, true);
      myModuleToTestsOutput.put(module, out);
      return out;
    }

  private final Map<Module, String> myModuleToOutput = new HashMap<Module, String>();

  private String getOutputDir(final Module module) {
      if (myModuleToOutput.containsKey(module)) {
        return myModuleToOutput.get(module);
      }
      final String out = CompilerPaths.getModuleOutputPath(module, false);
      myModuleToOutput.put(module, out);
      return out;
    }

  private int myProcessedFilesCount = 0;
  private int myTotalFilesToCompile = 0;
  private int myClassesCount = 0;
  private volatile String myModuleName = null;

  private void sourceFileProcessed() {
    myProcessedFilesCount++;
    updateStatistics();
  }

  private void updateStatistics() {
    final String msg;
    String moduleName = myModuleName;
    if (moduleName != null) {
      msg = CompilerBundle.message("statistics.files.classes.module", myProcessedFilesCount, myClassesCount, moduleName);
    }
    else {
      msg = CompilerBundle.message("statistics.files.classes", myProcessedFilesCount, myClassesCount);
    }
    myCompileContext.getProgressIndicator().setText2(msg);
    myCompileContext.getProgressIndicator().setFraction(1.0* myProcessedFilesCount /myTotalFilesToCompile);
  }

  private static Map<Module, List<VirtualFile>> buildModuleToFilesMap(final CompileContext context, final List<VirtualFile> files) {
    //assertion: all files are different
    final Map<Module, List<VirtualFile>> map = new THashMap<Module, List<VirtualFile>>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (VirtualFile file : files) {
          final Module module = context.getModuleByFile(file);

          if (module == null) {
            continue; // looks like file invalidated
          }

          List<VirtualFile> moduleFiles = map.get(module);
          if (moduleFiles == null) {
            moduleFiles = new ArrayList<VirtualFile>();
            map.put(module, moduleFiles);
          }
          moduleFiles.add(file);
        }
      }
    });
    return map;
  }

  private class ClassParsingThread implements Runnable {
    private final BlockingQueue<FileObject> myPaths = new ArrayBlockingQueue<FileObject>(50000);
    private CacheCorruptedException myError = null;
    private final boolean myAddNotNullAssertions;
    private final ModuleChunk myChunk;
    private final boolean myIsJdk16;

    private ClassParsingThread(ModuleChunk chunk, final boolean isJdk16) {
      myChunk = chunk;
      myIsJdk16 = isJdk16;
      myAddNotNullAssertions = CompilerWorkspaceConfiguration.getInstance(myProject).ASSERT_NOT_NULL;
    }

    volatile boolean processing;
    public void run() {
      processing = true;
      try {
        while (true) {
          FileObject path = myPaths.take();

          if (path == myStopThreadToken) break;
          processPath(path);
        }
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (CacheCorruptedException e) {
        myError = e;
      }
      processing = false;
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

    private void processPath(FileObject fileObject) throws CacheCorruptedException {
      File file = fileObject.getFile();
      byte[] fileContent = fileObject.getContent();
      String path = file.getPath();
      try {
        // the file is assumed to exist!
        final DependencyCache dependencyCache = myCompileContext.getDependencyCache();
        final int newClassQName = dependencyCache.reparseClassFile(file, fileContent);
        final Cache newClassesCache = dependencyCache.getNewClassesCache();
        final String sourceFileName = newClassesCache.getSourceFileName(newClassQName);
        final String qName = dependencyCache.resolve(newClassQName);
        String relativePathToSource = "/" + MakeUtil.createRelativePathToSource(qName, sourceFileName);
        putName(myChunk, sourceFileName, newClassQName, relativePathToSource, path);
        boolean haveToInstrument = myAddNotNullAssertions && hasNotNullAnnotations(newClassesCache, dependencyCache.getSymbolTable(), newClassQName);

        boolean fileContentChanged = false;
        if (haveToInstrument) {
          try {
            ClassReader reader = new ClassReader(fileContent, 0, fileContent.length);
            ClassWriter writer = new PsiClassWriter(myProject, myIsJdk16);

            final NotNullVerifyingInstrumenter instrumenter = new NotNullVerifyingInstrumenter(writer);
            reader.accept(instrumenter, 0);
            if (instrumenter.isModification()) {
              fileContent = writer.toByteArray();
              fileContentChanged = true;
            }
          }
          catch (Exception ignored) {
            LOG.info(ignored);
          }
        }

        if (fileContentChanged || !fileObject.isSaved()) {
          writeFile(file, fileContent);
        }
      }
      catch (ClsFormatException e) {
        String message;
        final String m = e.getMessage();
        if (m == null || "".equals(m)) {
          message = CompilerBundle.message("error.bad.class.file.format", path);
        }
        else {
          message = CompilerBundle.message("error.bad.class.file.format", m + "\n" + path);
        }
        myCompileContext.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
      }
      catch (IOException e) {
        myCompileContext.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
      }
      finally {
        myClassesCount++;
        updateStatistics();
      }
    }

    private void writeFile(File file, byte[] fileContent) throws IOException {
      try {
        FileUtil.writeToFile(file, fileContent);
      }
      catch (FileNotFoundException e) {
        FileUtil.createParentDirs(file);
        FileUtil.writeToFile(file, fileContent);
      }
    }
  }

  private static boolean hasNotNullAnnotations(final Cache cache, final SymbolTable symbolTable, final int className) throws CacheCorruptedException {
    for (MethodInfo methodId : cache.getMethods(className)) {
      for (AnnotationConstantValue annotation : methodId.getRuntimeInvisibleAnnotations()) {
        if (AnnotationUtil.NOT_NULL.equals(symbolTable.getSymbol(annotation.getAnnotationQName()))) {
          return true;
        }
      }
      final AnnotationConstantValue[][] paramAnnotations = methodId.getRuntimeInvisibleParameterAnnotations();
      for (AnnotationConstantValue[] _singleParamAnnotations : paramAnnotations) {
        for (AnnotationConstantValue annotation : _singleParamAnnotations) {
          if (AnnotationUtil.NOT_NULL.equals(symbolTable.getSymbol(annotation.getAnnotationQName()))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean isJdk6(final Sdk jdk) {
    boolean isJDK16 = false;
    if (jdk != null) {
      final String versionString = jdk.getVersionString();
      if (versionString != null) {
        isJDK16 = versionString.contains("1.6") || versionString.contains("6.0");
      }
    }
    return isJDK16;
  }
}
