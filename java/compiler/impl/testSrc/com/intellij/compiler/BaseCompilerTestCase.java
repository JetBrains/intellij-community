/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.testFramework.*;
import com.intellij.util.ParameterizedRunnable;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.io.TestFileSystemBuilder;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.util.JpsPathUtil;
import org.junit.Assert;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public abstract class BaseCompilerTestCase extends ModuleTestCase {

  @Override
  protected void setUpModule() {
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProject.getMessageBus().connect(getTestRootDisposable()).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        //todo[nik] projectOpened isn't called in tests so we need to add this listener manually
        forceFSRescan();
      }
    });
    CompilerTestUtil.enableExternalCompiler();
  }

  protected void forceFSRescan() {
    BuildManager.getInstance().clearState(myProject);
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      for (Artifact artifact : getArtifactManager().getArtifacts()) {
        final String outputPath = artifact.getOutputPath();
        if (!StringUtil.isEmpty(outputPath)) {
          FileUtil.delete(new File(FileUtil.toSystemDependentName(outputPath)));
        }
      }
      CompilerTestUtil.disableExternalCompiler(getProject());
    }
    finally {
      super.tearDown();
    }
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
      @Override
      protected void run(@NotNull final Result result) {
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
      protected void run(@NotNull final Result<Module> result) {
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
    return make(false, false, modules);
  }

  protected CompilationLog makeWithDependencies(final boolean includeRuntimeDependencies, Module... modules) {
    return make(true, includeRuntimeDependencies, modules);
  }

  private CompilationLog make(boolean includeDependentModules, final boolean includeRuntimeDependencies, Module... modules) {
    return make(getCompilerManager().createModulesCompileScope(modules, includeDependentModules, includeRuntimeDependencies), CompilerFilter.ALL);
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
    return compile(errorsExpected, callback -> {
      final CompilerManager compilerManager = getCompilerManager();
      if (forceCompile) {
        Assert.assertSame("Only 'ALL' filter is supported for forced compilation", CompilerFilter.ALL, filter);
        compilerManager.compile(scope, callback);
      }
      else {
        compilerManager.make(scope, filter, callback);
      }
    });
  }

  protected CompilationLog rebuild() {
    return compile(false, compileStatusNotification -> getCompilerManager().rebuild(compileStatusNotification));
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
    final List<String> generatedFilePaths = new ArrayList<>();
    getCompilerManager().addCompilationStatusListener(new CompilationStatusAdapter() {
      @Override
      public void fileGenerated(String outputRoot, String relativePath) {
        generatedFilePaths.add(relativePath);
      }
    }, getTestRootDisposable());
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {

        final CompileStatusNotification callback = new CompileStatusNotification() {
          @Override
          public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
            try {
              if (aborted) {
                Assert.fail("compilation aborted");
              }
              ExitStatus status = CompileDriver.getExternalBuildExitStatus(compileContext);
              result.set(new CompilationLog(status == ExitStatus.UP_TO_DATE,
                                            generatedFilePaths,
                                            compileContext.getMessages(CompilerMessageCategory.ERROR),
                                            compileContext.getMessages(CompilerMessageCategory.WARNING)));
            }
            finally {
              semaphore.up();
            }
          }
        };
        PlatformTestUtil.saveProject(myProject);
        CompilerTestUtil.saveApplicationSettings();
        action.run(callback);
      }
    });

    final long start = System.currentTimeMillis();
    while (!semaphore.waitFor(10)) {
      if (System.currentTimeMillis() - start > 5 * 60 * 1000) {
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

  protected void changeFile(VirtualFile file) {
    changeFile(file, null);
  }

  protected void changeFile(final VirtualFile file, @Nullable final String newText) {
    try {
      if (newText != null) {
        setFileText(file, newText);
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
      protected void run(@NotNull final Result result) {
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
    FileUtil.delete(iprFile);
    return iprFile;
  }

  @Override
  protected Module doCreateRealModule(String moduleName) {
    //todo[nik] reuse code from PlatformTestCase
    final VirtualFile baseDir = myProject.getBaseDir();
    Assert.assertNotNull(baseDir);
    final File moduleFile = new File(baseDir.getPath().replace('/', File.separatorChar), moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
    PlatformTestCase.myFilesToDelete.add(moduleFile);
    return new WriteAction<Module>() {
      @Override
      protected void run(@NotNull Result<Module> result) throws Throwable {
        Module module = ModuleManager.getInstance(myProject)
          .newModule(FileUtil.toSystemIndependentName(moduleFile.getAbsolutePath()), getModuleType().getId());
        module.getModuleFile();
        result.setResult(module);
      }
    }.execute().getResultObject();
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

  protected static class CompilationLog {
    private final Set<String> myGeneratedPaths;
    private final boolean myExternalBuildUpToDate;
    private final CompilerMessage[] myErrors;
    private final CompilerMessage[] myWarnings;

    public CompilationLog(boolean externalBuildUpToDate, List<String> generatedFilePaths, CompilerMessage[] errors,
                          CompilerMessage[] warnings) {
      myExternalBuildUpToDate = externalBuildUpToDate;
      myErrors = errors;
      myWarnings = warnings;
      myGeneratedPaths = new THashSet<>(generatedFilePaths, FileUtil.PATH_HASHING_STRATEGY);
    }

    public void assertUpToDate() {
      assertTrue(myExternalBuildUpToDate);
    }

    public void assertGenerated(String... expected) {
      assertSet("generated", myGeneratedPaths, expected);
    }

    public CompilerMessage[] getErrors() {
      return myErrors;
    }

    public CompilerMessage[] getWarnings() {
      return myWarnings;
    }

    private static void assertSet(String name, Set<String> actual, String[] expected) {
      for (String path : expected) {
        if (!actual.remove(path)) {
          Assert.fail("'" + path + "' is not " + name + ". " + name + ": " + new HashSet<>(actual));
        }
      }
      if (!actual.isEmpty()) {
        Assert.fail("'" + actual.iterator().next() + "' must not be " + name);
      }
    }
  }
}
