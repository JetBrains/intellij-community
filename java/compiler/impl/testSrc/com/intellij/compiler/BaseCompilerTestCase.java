package com.intellij.compiler;

import com.intellij.ProjectTopics;
import com.intellij.compiler.impl.CompileDriver;
import com.intellij.compiler.impl.ExitStatus;
import com.intellij.compiler.server.BuildManager;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.ParameterizedRunnable;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.io.TestFileSystemBuilder;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import junit.framework.Assert;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.util.JpsPathUtil;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public abstract class BaseCompilerTestCase extends ModuleTestCase {
  protected boolean useExternalCompiler() {
    return false;
  }

  @Override
  protected void setUpModule() {
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if (useExternalCompiler()) {
      myProject.getMessageBus().connect(myTestRootDisposable).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
        @Override
        public void rootsChanged(ModuleRootEvent event) {
          //todo[nik] projectOpened isn't called in tests so we need to add this listener manually
          forceFSRescan();
        }
      });
      CompilerTestUtil.enableExternalCompiler(myProject);
    }
    else {
      CompilerTestUtil.disableExternalCompiler(myProject);
    }
  }

  protected void forceFSRescan() {
    BuildManager.getInstance().clearState(myProject);
  }

  @Override
  protected Sdk getTestProjectJdk() {
    if (useExternalCompiler()) {
      return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    }
    return super.getTestProjectJdk();
  }

  @Override
  protected void tearDown() throws Exception {
    for (Artifact artifact : getArtifactManager().getArtifacts()) {
      final String outputPath = artifact.getOutputPath();
      if (!StringUtil.isEmpty(outputPath)) {
        FileUtil.delete(new File(FileUtil.toSystemDependentName(outputPath)));
      }
    }
    if (useExternalCompiler()) {
      CompilerTestUtil.disableExternalCompiler(myProject);
    }

    super.tearDown();
  }

  protected ArtifactManager getArtifactManager() {
    return ArtifactManager.getInstance(myProject);
  }

  protected String getProjectBasePath() {
    return getBaseDir().getPath();
  }

  protected VirtualFile getBaseDir() {
    final VirtualFile baseDir = myProject.getBaseDir();
    Assert.assertNotNull(baseDir);
    return baseDir;
  }

  protected void copyToProject(String relativePath) {
    File dir = PathManagerEx.findFileUnderProjectHome(relativePath, getClass());
    final File target = new File(FileUtil.toSystemDependentName(getProjectBasePath()));
    try {
      FileUtil.copyDir(dir, target);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    new WriteAction() {
      protected void run(final Result result) {
        VirtualFile virtualDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target);
        assertNotNull(target.getAbsolutePath() + " not found", virtualDir);
        virtualDir.refresh(false, true);
      }
    }.execute();
  }

  protected Module addModule(final String moduleName, final @Nullable VirtualFile sourceRoot) {
    return addModule(moduleName, sourceRoot, null);
  }

  protected Module addModule(final String moduleName, final @Nullable VirtualFile sourceRoot, final @Nullable VirtualFile testRoot) {
    return new WriteAction<Module>() {
      @Override
      protected void run(final Result<Module> result) {
        final Module module = createModule(moduleName);
        if (sourceRoot != null) {
          PsiTestUtil.addSourceContentToRoots(module, sourceRoot, false);
        }
        if (testRoot != null) {
          PsiTestUtil.addSourceContentToRoots(module, testRoot, true);
        }
        ModuleRootModificationUtil.setModuleSdk(module, getTestProjectJdk());
        result.setResult(module);
      }
    }.execute().getResultObject();
  }

  protected VirtualFile createFile(final String path) {
    return createFile(path, "");
  }

  protected VirtualFile createFile(final String path, final String text) {
    return VfsTestUtil.createFile(getBaseDir(), path, text);
  }

  protected CompilationLog make(final Artifact... artifacts) {
    final CompileScope scope = ArtifactCompileScope.createArtifactsScope(myProject, Arrays.asList(artifacts));
    return make(scope, CompilerFilter.ALL);
  }

  protected CompilationLog recompile(final Artifact... artifacts) {
    final CompileScope scope = ArtifactCompileScope.createArtifactsScope(myProject, Arrays.asList(artifacts), true);
    return make(scope, CompilerFilter.ALL);
  }

  protected CompilationLog make(Module... modules) {
    return make(getCompilerManager().createModulesCompileScope(modules, false), CompilerFilter.ALL);
  }

  protected CompilationLog recompile(Module... modules) {
    return compile(getCompilerManager().createModulesCompileScope(modules, false), CompilerFilter.ALL, true);
  }

  protected CompilerManager getCompilerManager() {
    return CompilerManager.getInstance(myProject);
  }

  protected void assertModulesUpToDate() {
    boolean upToDate = getCompilerManager().isUpToDate(getCompilerManager().createProjectCompileScope(myProject));
    assertTrue("Modules are not up to date", upToDate);
  }

  protected CompilationLog compile(boolean force, VirtualFile... files) {
    return compile(getCompilerManager().createFilesCompileScope(files), CompilerFilter.ALL, force);
  }

  protected CompilationLog make(final CompileScope scope, final CompilerFilter filter) {
    return compile(scope, filter, false);
  }

  protected CompilationLog compile(final CompileScope scope, final CompilerFilter filter, final boolean forceCompile) {
    return compile(scope, filter, forceCompile, false);
  }

  protected CompilationLog compile(final CompileScope scope, final CompilerFilter filter, final boolean forceCompile,
                                   final boolean errorsExpected) {
    return compile(errorsExpected, new ParameterizedRunnable<CompileStatusNotification>() {
      @Override
      public void run(CompileStatusNotification callback) {
        final CompilerManager compilerManager = getCompilerManager();
        if (forceCompile) {
          Assert.assertSame("Only 'ALL' filter is supported for forced compilation", CompilerFilter.ALL, filter);
          compilerManager.compile(scope, callback);
        }
        else {
          compilerManager.make(scope, filter, callback);
        }
      }
    });
  }

  protected CompilationLog rebuild() {
    return compile(false, new ParameterizedRunnable<CompileStatusNotification>() {
      @Override
      public void run(CompileStatusNotification compileStatusNotification) {
        getCompilerManager().rebuild(compileStatusNotification);
      }
    });
  }

  protected CompilationLog compile(final boolean errorsExpected, final ParameterizedRunnable<CompileStatusNotification> action) {
    CompilationLog log = compile(action);
    if (errorsExpected && log.myErrors.length == 0) {
      Assert.fail("compilation finished without errors");
    }
    else if (!errorsExpected && log.myErrors.length > 0) {
      Assert.fail("compilation finished with errors: " + Arrays.toString(log.myErrors));
    }
    return log;
  }

  private CompilationLog compile(final ParameterizedRunnable<CompileStatusNotification> action) {
    final Ref<CompilationLog> result = Ref.create(null);
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {

        CompilerManagerImpl.testSetup();
        final CompileStatusNotification callback = new CompileStatusNotification() {
          @Override
          public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
            try {
              if (aborted) {
                Assert.fail("compilation aborted");
              }
              ExitStatus status = CompileDriver.getExternalBuildExitStatus(compileContext);
              result.set(new CompilationLog(status == ExitStatus.UP_TO_DATE,
                                            CompilerManagerImpl.getPathsToRecompile(), CompilerManagerImpl.getPathsToDelete(),
                                            compileContext.getMessages(CompilerMessageCategory.ERROR),
                                            compileContext.getMessages(CompilerMessageCategory.WARNING)));
            }
            finally {
              semaphore.up();
            }
          }
        };
        if (useExternalCompiler()) {
          myProject.save();
          CompilerTestUtil.saveApplicationSettings();
          CompilerTestUtil.scanSourceRootsToRecompile(myProject);
        }
        action.run(callback);
      }
    });

    final long start = System.currentTimeMillis();
    while (!semaphore.waitFor(10)) {
      if (System.currentTimeMillis() - start > 60 * 1000) {
        throw new RuntimeException("timeout");
      }
      if (SwingUtilities.isEventDispatchThread()) {
        UIUtil.dispatchAllInvocationEvents();
      }
    }
    if (SwingUtilities.isEventDispatchThread()) {
      UIUtil.dispatchAllInvocationEvents();
    }

    return result.get();
  }

  private Set<String> getRelativePaths(String[] paths) {
    final Set<String> set = new THashSet<String>();
    final String basePath = myProject.getBaseDir().getPath();
    for (String path : paths) {
      set.add(StringUtil.trimStart(StringUtil.trimStart(FileUtil.toSystemIndependentName(path), basePath), "/"));
    }
    return set;
  }

  protected void changeFile(VirtualFile file) {
    changeFile(file, null);
  }

  protected void changeFile(VirtualFile file, final String newText) {
    try {
      if (newText != null) {
        VfsUtil.saveText(file, newText);
      }
      ((NewVirtualFile)file).setTimeStamp(file.getTimeStamp() + 10);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void deleteFile(final VirtualFile file) {
    new WriteAction() {
      @Override
      protected void run(final Result result) {
        try {
          file.delete(this);
        }
        catch (IOException e) {
          throw new AssertionError(e);
        }
      }
    }.execute();
  }

  @Override
  protected void setUpProject() throws Exception {
    super.setUpProject();
    final String baseUrl = myProject.getBaseDir().getUrl();
    CompilerProjectExtension.getInstance(myProject).setCompilerOutputUrl(baseUrl + "/out");
  }

  @Override
  protected File getIprFile() throws IOException {
    File iprFile = super.getIprFile();
    if (useExternalCompiler()) {
      FileUtil.delete(iprFile);
    }
    return iprFile;
  }

  @Override
  protected Module doCreateRealModule(String moduleName) {
    if (useExternalCompiler()) {
      //todo[nik] reuse code from PlatformTestCase
      final VirtualFile baseDir = myProject.getBaseDir();
      Assert.assertNotNull(baseDir);
      final File moduleFile = new File(baseDir.getPath().replace('/', File.separatorChar), moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
      PlatformTestCase.myFilesToDelete.add(moduleFile);
      return new WriteAction<Module>() {
        @Override
        protected void run(Result<Module> result) throws Throwable {
          Module module = ModuleManager.getInstance(myProject)
            .newModule(FileUtil.toSystemIndependentName(moduleFile.getAbsolutePath()), getModuleType().getId());
          module.getModuleFile();
          result.setResult(module);
        }
      }.execute().getResultObject();
    }
    return super.doCreateRealModule(moduleName);
  }

  @Override
  protected boolean isRunInWriteAction() {
    return false;
  }

  protected static void assertOutput(Module module, TestFileSystemBuilder item) {
    assertOutput(module, item, false);
  }

  protected static void assertOutput(Module module, TestFileSystemBuilder item, final boolean forTests) {
    File outputDir = getOutputDir(module, forTests);
    Assert.assertTrue((forTests? "Test output" : "Output") +" directory " + outputDir.getAbsolutePath() + " doesn't exist", outputDir.exists());
    item.build().assertDirectoryEqual(outputDir);
  }

  protected static void assertNoOutput(Module module) {
    File dir = getOutputDir(module);
    Assert.assertFalse("Output directory " + dir.getAbsolutePath() + " does exist", dir.exists());
  }

  protected static File getOutputDir(Module module) {
    return getOutputDir(module, false);
  }

  protected static File getOutputDir(Module module, boolean forTests) {
    CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
    Assert.assertNotNull(extension);
    String outputUrl = forTests? extension.getCompilerOutputUrlForTests() : extension.getCompilerOutputUrl();
    Assert.assertNotNull((forTests? "Test output" : "Output") +" directory for module '" + module.getName() + "' isn't specified", outputUrl);
    return JpsPathUtil.urlToFile(outputUrl);
  }

  protected static void createFileInOutput(Module m, final String fileName) {
    try {
      boolean created = new File(getOutputDir(m), fileName).createNewFile();
      assertTrue(created);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static void createFileInOutput(Artifact a, final String name)  {
    try {
      boolean created = new File(a.getOutputPath(), name).createNewFile();
      assertTrue(created);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected class CompilationLog {
    private final Set<String> myRecompiledPaths;
    private final Set<String> myDeletedPaths;
    private final boolean myExternalBuildUpToDate;
    private final CompilerMessage[] myErrors;
    private final CompilerMessage[] myWarnings;

    public CompilationLog(boolean externalBuildUpToDate, String[] recompiledPaths, String[] deletedPaths, CompilerMessage[] errors, CompilerMessage[] warnings) {
      myExternalBuildUpToDate = externalBuildUpToDate;
      myErrors = errors;
      myWarnings = warnings;
      myRecompiledPaths = getRelativePaths(recompiledPaths);
      myDeletedPaths = getRelativePaths(deletedPaths);
    }

    public void assertUpToDate() {
      if (useExternalCompiler()) {
        assertTrue(myExternalBuildUpToDate);
      }
      else {
        checkRecompiled();
        checkDeleted();
      }
    }

    public void assertRecompiled(String... expected) {
      checkRecompiled(expected);
      checkDeleted();
    }

    public void assertDeleted(String... expected) {
      checkRecompiled();
      checkDeleted(expected);
    }

    public void assertRecompiledAndDeleted(String[] recompiled, String... deleted) {
      checkRecompiled(recompiled);
      checkDeleted(deleted);
    }

    private void checkRecompiled(String... expected) {
      assertSet("recompiled", myRecompiledPaths, expected);
    }

    private void checkDeleted(String... expected) {
      assertSet("deleted", myDeletedPaths, expected);
    }

    public CompilerMessage[] getErrors() {
      return myErrors;
    }

    public CompilerMessage[] getWarnings() {
      return myWarnings;
    }

    private void assertSet(String name, Set<String> actual, String[] expected) {
      if (useExternalCompiler()) return;
      for (String path : expected) {
        if (!actual.remove(path)) {
          Assert.fail("'" + path + "' is not " + name + ". " + name + ": " + new HashSet<String>(actual));
        }
      }
      if (!actual.isEmpty()) {
        Assert.fail("'" + actual.iterator().next() + "' must not be " + name);
      }
    }
  }
}
