/*
 * @author: Eugene Zhuravlev
 * Date: Jan 24, 2003
 * Time: 4:25:47 PM
 */
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.*;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.make.Cache;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.compiler.make.MakeUtil;
import com.intellij.compiler.make.SourceFileFinder;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.cls.ClsFormatException;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;


public class BackendCompilerWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.BackendCompilerWrapper");

  private final BackendCompiler myCompiler;
  private final Map<String, Set<CompiledClass>> myFileNameToSourceMap;
  private final List<File> myFilesToRefresh;
  private final Set<VirtualFile> mySuccesfullyCompiledJavaFiles; // VirtualFile
  private final List<TranslatingCompiler.OutputItem> myOutputItems;

  private final CompileContextEx myCompileContext;
  private final VirtualFile[] myFilesToCompile;
  private final Project myProject;
  private Set<VirtualFile> myFilesToRecompile = Collections.emptySet();
  private Map<Module, VirtualFile> myModuleToTempDirMap = new HashMap<Module, VirtualFile>();
  private final ProjectFileIndex myProjectFileIndex;
  @NonNls private static final String PACKAGE_ANNOTATION_FILE_NAME = "package-info.java";

  public BackendCompilerWrapper(final Project project,
                                VirtualFile[] filesToCompile,
                                CompileContextEx compileContext,
                                BackendCompiler compiler) {
    myProject = project;
    myCompiler = compiler;
    myCompileContext = compileContext;
    myFilesToCompile = filesToCompile;
    myProjectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    mySuccesfullyCompiledJavaFiles = new HashSet<VirtualFile>(filesToCompile.length);
    myOutputItems = new ArrayList<TranslatingCompiler.OutputItem>(filesToCompile.length);
    myFileNameToSourceMap = new HashMap<String, Set<CompiledClass>>(filesToCompile.length);
    myFilesToRefresh = new ArrayList<File>(filesToCompile.length);
  }

  public TranslatingCompiler.OutputItem[] compile() throws CompilerException, CacheCorruptedException {
    VirtualFile[] dependentFiles = null;
    Application application = ApplicationManager.getApplication();
    COMPILE:
    try {
      if (myFilesToCompile.length > 0) {
        if (application.isUnitTestMode()) {
          saveTestData();
        }

        final Map<Module, Set<VirtualFile>> moduleToFilesMap = buildModuleToFilesMap(myCompileContext, myFilesToCompile);
        compileModules(moduleToFilesMap);
      }

      dependentFiles = findDependentFiles();

      if (myCompileContext.getProgressIndicator().isCanceled() || myCompileContext.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        break COMPILE;
      }

      if (dependentFiles.length > 0) {
        VirtualFile[] filesInScope = getFilesInScope(dependentFiles);
        if (filesInScope.length > 0) {
          final Map<Module, Set<VirtualFile>> moduleToFilesMap = buildModuleToFilesMap(myCompileContext, filesInScope);
          compileModules(moduleToFilesMap);
        }
      }
    }
    catch (IOException e) {
      throw new CompilerException(CompilerBundle.message("error.compiler.process.not.started", e.getMessage()), e);
    }
    catch (SecurityException e) {
      throw new CompilerException(CompilerBundle.message("error.compiler.process.not.started", e.getMessage()), e);
    }
    catch (IllegalArgumentException e) {
      throw new CompilerException(e.getMessage(), e);
    }
    finally {
      myCompileContext.getProgressIndicator().pushState();
      myCompileContext.getProgressIndicator().setText(CompilerBundle.message("progress.deleting.temp.files"));
      for (final Module module : myModuleToTempDirMap.keySet()) {
        final VirtualFile file = myModuleToTempDirMap.get(module);
        if (file != null) {
          final File ioFile = application.runReadAction(new Computable<File>() {
            public File compute() {
              return new File(file.getPath());
            }
          });
          FileUtil.asyncDelete(ioFile);
        }
      }
      myModuleToTempDirMap.clear();
      if (!myCompileContext.getProgressIndicator().isCanceled()) {
        // do not update caches if cancelled because there is a chance that they will be incomplete
        myCompileContext.getProgressIndicator().setText(CompilerBundle.message("progress.updating.caches"));
        if (mySuccesfullyCompiledJavaFiles.size() > 0 || (dependentFiles != null && dependentFiles.length > 0)) {
          myCompileContext.getDependencyCache().update();
        }
      }
      myCompileContext.getProgressIndicator().popState();
    }
    if (!myCompileContext.getProgressIndicator().isCanceled()) {
      myFilesToRecompile = new HashSet<VirtualFile>(Arrays.asList(myFilesToCompile));
      if (dependentFiles != null) {
        myFilesToRecompile.addAll(Arrays.asList(dependentFiles));
      }
      myFilesToRecompile.removeAll(mySuccesfullyCompiledJavaFiles);
      processPackageInfoFiles();

      return myOutputItems.toArray(new TranslatingCompiler.OutputItem[myOutputItems.size()]);
    }
    else {
      // when cancelled pretend that nothing was compiled and next compile will compile everything from the scratch
      return TranslatingCompiler.EMPTY_OUTPUT_ITEM_ARRAY;
    }
  }

  // package-info.java hack
  private void processPackageInfoFiles() {
    if (myFilesToRecompile.size() == 0) {
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
        if (packageInfoFiles.size() > 0) {
          final Set<VirtualFile> badFiles = getFilesCompiledWithErrors();
          for (final VirtualFile packageInfoFile : packageInfoFiles) {
            if (!badFiles.contains(packageInfoFile)) {
              myOutputItems.add(new OutputItemImpl(null, null, packageInfoFile));
              myFilesToRecompile.remove(packageInfoFile);
            }
          }
        }
      }
    });
  }

  private VirtualFile[] getFilesInScope(final VirtualFile[] dependentFiles) {
    final List<VirtualFile> filesInScope = new ArrayList<VirtualFile>(dependentFiles.length);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (VirtualFile dependentFile : dependentFiles) {
          if (myCompileContext.getCompileScope().belongs(dependentFile.getUrl())) {
            filesInScope.add(dependentFile);
          }
        }
      }
    });
    return filesInScope.toArray(new VirtualFile[filesInScope.size()]);
  }

  private void compileModules(final Map<Module, Set<VirtualFile>> moduleToFilesMap) throws IOException {
    final List<ModuleChunk> chunks = getModuleChunks(moduleToFilesMap);

    for (final ModuleChunk chunk : chunks) {
      runTransformingCompilers(chunk);

      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          final Module[] modules = chunk.getModules();
          final StringBuilder names = StringBuilderSpinAllocator.alloc();
          try {
            for (int idx = 0; idx < modules.length; idx++) {
              Module module = modules[idx];
              if (idx > 0) {
                names.append(", ");
              }
              names.append(module.getName());
            }
            myModuleName = names.toString();
          }
          finally {
            StringBuilderSpinAllocator.dispose(names);
          }
        }
      });

      File fileToDelete = null;
      final List<OutputDir> pairs = new ArrayList<OutputDir>();
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
              pairs.add(new OutputDir(testsOutputDir, ModuleChunk.TEST_SOURCES));
            }
            else { // both sources and test sources go into the same output
              if (sourcesOutputDir == null) {
                LOG.error("Sources output dir is null for module \"" + module.getName() + "\"");
              }
              pairs.add(new OutputDir(sourcesOutputDir, ModuleChunk.ALL_SOURCES));
            }
          }
        });
      }
      else { // chunk has several modules
        final File outputDir = FileUtil.createTempDirectory("compile", "output");
        fileToDelete = outputDir;
        pairs.add(new OutputDir(outputDir.getPath(), ModuleChunk.ALL_SOURCES));
      }

      try {
        for (final OutputDir outputDir : pairs) {
          doCompile(chunk, outputDir.getPath(), outputDir.getKind());
          if (myCompileContext.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
            return;
          }
        }
      }
      finally {
        if (fileToDelete != null) {
          FileUtil.asyncDelete(fileToDelete);
        }
      }
    }
  }

  private List<ModuleChunk> getModuleChunks(final Map<Module, Set<VirtualFile>> moduleToFilesMap) {
    final List<Module> modules = new ArrayList<Module>(moduleToFilesMap.keySet());
    final List<Chunk<Module>> chunks = ApplicationManager.getApplication().runReadAction(new Computable<List<Chunk<Module>>>() {
      public List<Chunk<Module>> compute() {
        return ModuleCompilerUtil.getSortedModuleChunks(myProject, modules.toArray(new Module[modules.size()]));
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
    return !moduleTestOutputDirectory.equals(moduleOutputDirectory);
  }

  public VirtualFile[] getFilesToRecompile() {
    return myFilesToRecompile.toArray(new VirtualFile[myFilesToRecompile.size()]);
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

  private VirtualFile[] findDependentFiles() throws CacheCorruptedException {
    myCompileContext.getProgressIndicator().setText(CompilerBundle.message("progress.checking.dependencies"));
    final int[] dependentClassInfos =
      myCompileContext.getDependencyCache().findDependentClasses(myCompileContext, myProject, mySuccesfullyCompiledJavaFiles);
    final Set<VirtualFile> dependentFiles = new HashSet<VirtualFile>();
    final CacheCorruptedException[] _ex = new CacheCorruptedException[]{null};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(myProject);
          SourceFileFinder sourceFileFinder = new SourceFileFinder(myProject, myCompileContext);
          final Cache cache = myCompileContext.getDependencyCache().getCache();
          for (final int infoQName : dependentClassInfos) {
            final String qualifiedName = myCompileContext.getDependencyCache().resolve(infoQName);
            final VirtualFile file = sourceFileFinder.findSourceFile(qualifiedName, cache.getSourceFileName(cache.getClassId(infoQName)));
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
                LOG.debug("No source file for " + myCompileContext.getDependencyCache().resolve(infoQName) + " found");
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

    return dependentFiles.toArray(new VirtualFile[dependentFiles.size()]);
  }

  private final Object lock = new Object();

  private class SynchedCompilerParsing extends CompilerParsingThreadImpl {
    private final ClassParsingThread myClassParsingThread;

    public SynchedCompilerParsing(Process process,
                                  final CompileContext context,
                                  OutputParser outputParser,
                                  ClassParsingThread classParsingThread,
                                  boolean readErrorStream,
                                  boolean trimLines) {
      super(process, context, outputParser, readErrorStream, trimLines);
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

    protected void processCompiledClass(final String classFileToProcess) throws CacheCorruptedException {
      synchronized (lock) {
        myClassParsingThread.addPath(classFileToProcess);
      }
    }
  }

  private void doCompile(final ModuleChunk chunk, String outputDir, int sourcesFilter) throws IOException {
    myCompileContext.getProgressIndicator().checkCanceled();
    chunk.setSourcesFilter(sourcesFilter);

    if (ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return chunk.getFilesToCompile().length == 0 ? Boolean.TRUE : Boolean.FALSE;
      }
    }).booleanValue()) {
      return; // should not invoke javac with empty sources list
    }

    int exitValue = 0;
    try {
      Process process = myCompiler.launchProcess(chunk, outputDir, myCompileContext);
      final ClassParsingThread classParsingThread = new ClassParsingThread();
      classParsingThread.start();
      OutputParser errorParser = myCompiler.createErrorParser(outputDir);
      CompilerParsingThread errorParsingThread = errorParser == null
                                                 ? null
                                                 : new SynchedCompilerParsing(process, myCompileContext, errorParser, classParsingThread,
                                                                              true, errorParser.isTrimLines());
      if (errorParsingThread != null) {
        errorParsingThread.start();
      }

      OutputParser outputParser = myCompiler.createOutputParser(outputDir);
      CompilerParsingThread outputParsingThread = outputParser == null
                                                  ? null
                                                  : new SynchedCompilerParsing(process, myCompileContext, outputParser, classParsingThread,
                                                                               false, outputParser.isTrimLines());
      if (outputParsingThread != null) {
        outputParsingThread.start();
      }

      try {
        exitValue = process.waitFor();
      }
      catch (InterruptedException e) {
        process.destroy();
        exitValue = process.exitValue();
      }

      joinThread(errorParsingThread);
      joinThread(outputParsingThread);
      classParsingThread.stopParsing();
      joinThread(classParsingThread);

      registerParsingException(outputParsingThread);
      registerParsingException(errorParsingThread);
    }
    finally {
      compileFinished(exitValue, chunk, outputDir);
      myModuleName = null;
    }
  }

  private static void joinThread(final Thread thread) {
    if (thread != null) {
      try {
        thread.join();
      }
      catch (InterruptedException e) {
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
            VirtualFile[] filesToCompile = chunk.getFilesToCompile(module);
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
        final VirtualFile[] filesToCompile = chunk.getFilesToCompile(module);
        for (int j = 0; j < filesToCompile.length; j++) {
          final VirtualFile file = filesToCompile[j];
          VirtualFile fileCopy = originalToCopyFileMap.get(file);
          if (fileCopy != null) {
            final boolean ok = transformer.transform(myCompileContext, fileCopy, file);
            if (ok) {
              filesToCompile[j] = fileCopy;
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
      myCompileContext
        .addMessage(CompilerMessageCategory.ERROR, CompilerBundle.message("error.compiler.internal.error", exitValue), null, -1, -1);
    }
    myCompiler.compileFinished();
    final VirtualFile[] sourceRoots = chunk.getSourceRoots();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final Set<VirtualFile> compiledWithErrors = getFilesCompiledWithErrors();
        final FileTypeManager typeManager = FileTypeManager.getInstance();
        final String outputDirPath = outputDir.replace(File.separatorChar, '/');
        if (LOG.isDebugEnabled()) {
          LOG.debug("myFileNameToSourceMap contains entries: " + myFileNameToSourceMap.size());
        }
        try {
          for (final VirtualFile root : sourceRoots) {
            final String packagePrefix = myProjectFileIndex.getPackageNameByDirectory(root);
            if (LOG.isDebugEnabled()) {
              LOG.debug("Building output items for " + root.getPresentableUrl() + "; output dir = " + outputDirPath + "; packagePrefix = \"" +
                        packagePrefix + "\"");
            }
            buildOutputItemsList(outputDirPath, root, typeManager, compiledWithErrors, root, packagePrefix);
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
    CompilerUtil.refreshIOFiles(myFilesToRefresh);
    myFileNameToSourceMap.clear(); // clear the map before the next use
    myFilesToRefresh.clear();
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

  private void buildOutputItemsList(final String outputDir,
                                    VirtualFile from,
                                    FileTypeManager typeManager,
                                    Set<VirtualFile> compiledWithErrors,
                                    final VirtualFile sourceRoot,
                                    final String packagePrefix) throws CacheCorruptedException {
    final VirtualFile[] children = from.getChildren();
    for (final VirtualFile child : children) {
      if (child.isDirectory()) {
        buildOutputItemsList(outputDir, child, typeManager, compiledWithErrors, sourceRoot, packagePrefix);
      }
      else if (StdFileTypes.JAVA.equals(typeManager.getFileTypeByFile(child))) {
        updateOutputItemsList(outputDir, child, compiledWithErrors, sourceRoot, packagePrefix);
      }
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

  private void updateOutputItemsList(
    final String outputDir, VirtualFile javaFile, Set<VirtualFile> compiledWithErrors, VirtualFile sourceRoot, final String packagePrefix) throws CacheCorruptedException {

    final Cache newCache = myCompileContext.getDependencyCache().getNewClassesCache();
    final Set<CompiledClass> paths = myFileNameToSourceMap.get(javaFile.getName());
    if (paths != null && paths.size() > 0) {
      final String prefix = packagePrefix != null && packagePrefix.length() > 0 ? packagePrefix.replace('.', '/') + "/" : "";
      final String filePath = "/" + prefix + VfsUtil.getRelativePath(javaFile, sourceRoot, '/');

      for (final CompiledClass cc : paths) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Checking [pathToClass; relPathToSource] = " + cc);
        }
        if (FileUtil.pathsEqual(filePath, cc.relativePathToSource)) {
          final String outputPath = cc.pathToClass.replace(File.separatorChar, '/');
          final Pair<String, String> realLocation = moveToRealLocation(outputDir, outputPath, javaFile);
          if (realLocation != null) {
            myOutputItems.add(new OutputItemImpl(realLocation.getFirst(), realLocation.getSecond(), javaFile));
            newCache.setPath(newCache.getClassId(cc.qName), realLocation.getSecond());
            if (LOG.isDebugEnabled()) {
              LOG.debug("Added output item: [outputDir; outputPath; sourceFile]  = [" + realLocation.getFirst() + "; " +
                        realLocation.getSecond() + "; " + javaFile.getPresentableUrl() + "]");
            }
            if (!compiledWithErrors.contains(javaFile)) {
              mySuccesfullyCompiledJavaFiles.add(javaFile);
            }
          }
          else {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Failed to move to real location: " + outputPath + "; from " + outputDir);
            }
          }
        }
      }
    }
  }

  private Pair<String, String> moveToRealLocation(String tempOutputDir, String pathToClass, VirtualFile sourceFile) {
    final Module module = myCompileContext.getModuleByFile(sourceFile);
    if (module == null) {
      // do not move: looks like source file has been invalidated, need recompilation
      return null;
    }
    final String realOutputDir;
    if (myProjectFileIndex.isInTestSourceContent(sourceFile)) {
      realOutputDir = getTestsOutputDir(module);
    }
    else {
      realOutputDir = getOutputDir(module);
    }

    if (FileUtil.pathsEqual(tempOutputDir, realOutputDir)) { // no need to move
      myFilesToRefresh.add(new File(pathToClass));
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
      myFilesToRefresh.add(toFile);
      return new Pair<String, String>(realOutputDir, realPathToClass);
    }
    return null;
  }

  private final Map<Module, String> myModuleToTestsOutput = new HashMap<Module, String>();

  private String getTestsOutputDir(final Module module) {
    if (myModuleToTestsOutput.containsKey(module)) {
      return myModuleToTestsOutput.get(module);
    }
    final VirtualFile outputDir = myCompileContext.getModuleOutputDirectoryForTests(module);
    final String out = outputDir != null ? outputDir.getPath() : null;
    myModuleToTestsOutput.put(module, out);
    return out;
  }

  private final Map<Module, String> myModuleToOutput = new HashMap<Module, String>();

  private String getOutputDir(final Module module) {
    if (myModuleToOutput.containsKey(module)) {
      return myModuleToOutput.get(module);
    }
    final VirtualFile outputDir = myCompileContext.getModuleOutputDirectory(module);
    final String out = outputDir != null ? outputDir.getPath() : null;
    myModuleToOutput.put(module, out);
    return out;
  }

  private int myFilesCount = 0;
  private int myClassesCount = 0;
  private String myModuleName = null;

  private void sourceFileProcessed() {
    myFilesCount += 1;
    updateStatistics();
  }

  private void updateStatistics() {
    final String msg;
    if (myModuleName != null) {
      msg = CompilerBundle.message("statistics.files.classes.module", myFilesCount, myClassesCount, myModuleName);
    }
    else {
      msg = CompilerBundle.message("statistics.files.classes", myFilesCount, myClassesCount);
    }
    myCompileContext.getProgressIndicator().setText2(msg);
  }

  private static Map<Module, Set<VirtualFile>> buildModuleToFilesMap(final CompileContext context, final VirtualFile[] files) {
    final Map<Module, Set<VirtualFile>> map = new HashMap<Module, Set<VirtualFile>>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (VirtualFile file : files) {
          final Module module = context.getModuleByFile(file);

          if (module == null) {
            continue; // looks like file invalidated
          }

          Set<VirtualFile> moduleFiles = map.get(module);
          if (moduleFiles == null) {
            moduleFiles = new HashSet<VirtualFile>();
            map.put(module, moduleFiles);
          }
          moduleFiles.add(file);
        }
      }
    });
    return map;
  }

  private class ClassParsingThread extends Thread {
    private final BlockingQueue<String> myPaths = new ArrayBlockingQueue<String>(50000);
    private CacheCorruptedException myError = null;
    private final String myStopThreadToken = new String();

    public ClassParsingThread() {
      //noinspection HardCodedStringLiteral
      super("Class Parsing Thread");
    }

    public void run() {
      String path;
      try {
        while ((path = getNextPath()) != myStopThreadToken) {
          processPath(path.replace('/', File.separatorChar));
        }
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (CacheCorruptedException e) {
        myError = e;
      }
    }

    public void addPath(String path) throws CacheCorruptedException {
      if (myError != null) {
        throw myError;
      }
      myPaths.offer(path);
    }

    private String getNextPath() throws InterruptedException {
      return myPaths.take();
    }

    public void stopParsing() {
      myPaths.offer(myStopThreadToken);
    }

    private void processPath(final String path) throws CacheCorruptedException {
      try {
        final File file = new File(path); // the file is assumed to exist!
        final int newClassQName = myCompileContext.getDependencyCache().reparseClassFile(file);
        final Cache newClassesCache = myCompileContext.getDependencyCache().getNewClassesCache();
        final String sourceFileName = newClassesCache.getSourceFileName(newClassesCache.getClassId(newClassQName));
        String relativePathToSource =
          "/" + MakeUtil.createRelativePathToSource(myCompileContext.getDependencyCache().resolve(newClassQName), sourceFileName);
        putName(sourceFileName, newClassQName, relativePathToSource, path);
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
      finally {
        myClassesCount += 1;
        updateStatistics();
      }
    }
  }

  private static class OutputDir {
    private final String myPath;
    private final int myKind;

    public OutputDir(String path, int kind) {
      myPath = path;
      myKind = kind;
    }

    public String getPath() {
      return myPath;
    }

    public int getKind() {
      return myKind;
    }

    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof OutputDir)) {
        return false;
      }

      final OutputDir outputDir = (OutputDir)o;

      if (myKind != outputDir.myKind) {
        return false;
      }
      if (!myPath.equals(outputDir.myPath)) {
        return false;
      }

      return true;
    }

    public int hashCode() {
      int result = myPath.hashCode();
      result = 29 * result + myKind;
      return result;
    }
  }

  private static final class CompiledClass {
    public final int qName;
    public final String relativePathToSource;
    public final String pathToClass;

    public CompiledClass(final int qName, final String relativePathToSource, final String pathToClass) {
      this.qName = qName;
      this.relativePathToSource = relativePathToSource;
      this.pathToClass = pathToClass;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final CompiledClass that = (CompiledClass)o;

      if (qName != that.qName) return false;
      if (!pathToClass.equals(that.pathToClass)) return false;
      if (!relativePathToSource.equals(that.relativePathToSource)) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = qName;
      result = 31 * result + relativePathToSource.hashCode();
      result = 31 * result + pathToClass.hashCode();
      return result;
    }

    public String toString() {
      return "[" + pathToClass + ";" + relativePathToSource + "]";
    }
  }

}
