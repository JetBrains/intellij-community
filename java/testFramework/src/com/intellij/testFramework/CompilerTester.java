// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.compiler.CompilerTestUtil;
import com.intellij.compiler.CompilerTests;
import com.intellij.compiler.server.BuildManager;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.execution.wsl.WslPath;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.components.impl.stores.IComponentStoreKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.cmdline.LogSetup;
import org.junit.Assert;

import javax.swing.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.intellij.configurationStore.StoreUtilKt.getPersistentStateComponentStorageLocation;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class CompilerTester {
  private static final Logger LOG = Logger.getInstance(CompilerTester.class);

  private final Project myProject;
  private List<? extends Module> myModules;
  private TempDirTestFixture myMainOutput;

  public CompilerTester(@NotNull Module module) throws Exception {
    this(module.getProject(), Collections.singletonList(module), null);
  }

  public CompilerTester(@NotNull IdeaProjectTestFixture fixture, @NotNull List<? extends Module> modules) throws Exception {
    this(fixture.getProject(), modules, fixture.getTestRootDisposable());
  }

  public CompilerTester(@NotNull Project project,
                        @NotNull List<? extends Module> modules,
                        @Nullable Disposable disposable) throws Exception {
    this(project, modules, disposable, true);
  }

  public CompilerTester(@NotNull Project project,
                        @NotNull List<? extends Module> modules,
                        @Nullable Disposable disposable,
                        boolean overrideJdkAndOutput) throws Exception {
    myProject = project;
    myModules = modules;
    myMainOutput = new TempDirTestFixtureImpl();
    myMainOutput.setUp();

    if (disposable != null) {
      Disposer.register(disposable, new Disposable() {
        @Override
        public void dispose() {
          tearDown();
        }
      });
    }

    CompilerTestUtil.enableExternalCompiler();
    if (overrideJdkAndOutput) {
      WriteCommandAction.writeCommandAction(getProject()).run(() -> {
        Objects.requireNonNull(CompilerProjectExtension.getInstance(getProject())).setCompilerOutputUrl(myMainOutput.findOrCreateDir("out").getUrl());
        if (!myModules.isEmpty()) {
          JavaAwareProjectJdkTableImpl projectJdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx();
          if ((project.getBasePath() != null) && (WslPath.getDistributionByWindowsUncPath(project.getBasePath()) == null)) {
            for (Module module : myModules) {
              ModuleRootModificationUtil.setModuleSdk(module, projectJdkTable.getInternalJdk());
            }
          }
        }
      });
      IndexingTestUtil.waitUntilIndexesAreReady(project);
    }
  }

  public void tearDown() {
    try {
      RunAll.runAll(
        () -> myMainOutput.tearDown(),
        () -> CompilerTestUtil.disableExternalCompiler(getProject()),
        () -> IComponentStoreKt.getStateStore(ApplicationManager.getApplication()).clearCaches()
      );
    }
    finally {
      myMainOutput = null;
      myModules = null;
    }
  }

  private Project getProject() {
    return myProject;
  }

  public void deleteClassFile(@NotNull String className) throws IOException {
    WriteAction.runAndWait(() -> {
      //noinspection ConstantConditions
      touch(JavaPsiFacade.getInstance(getProject()).findClass(className, GlobalSearchScope.allScope(getProject())).getContainingFile().getVirtualFile());
    });
  }

  @Nullable
  public File findClassFile(String className, Module module) {
    VirtualFile out = ModuleRootManager.getInstance(module).getModuleExtension(CompilerModuleExtension.class).getCompilerOutputPath();
    assertNotNull(out);
    File cls = new File(out.getPath(), className.replace('.', '/') + ".class");
    return cls.exists() ? cls : null;
  }

  public void touch(final VirtualFile file) throws IOException {
    WriteAction.runAndWait(() -> {
      file.setBinaryContent(file.contentsToByteArray(), -1, file.getTimeStamp() + 1);
      File ioFile = VfsUtilCore.virtualToIoFile(file);
      assertTrue(ioFile.setLastModified(ioFile.lastModified() - 100000));
      file.refresh(false, false);
    });
  }

  public void setFileText(final PsiFile file, final String text) throws IOException {
    WriteAction.runAndWait(() -> {
      final VirtualFile virtualFile = file.getVirtualFile();
      VfsUtil.saveText(Objects.requireNonNull(virtualFile), text);
    });
    touch(file.getVirtualFile());
  }

  public void setFileName(final PsiFile file, final String name) {
    WriteCommandAction.writeCommandAction(getProject()).run(() -> file.setName(name));
  }

  public List<CompilerMessage> make() {
    return runCompiler(callback -> CompilerManager.getInstance(getProject()).make(callback));
  }

  public List<CompilerMessage> rebuild() {
    return runCompiler(callback -> CompilerManager.getInstance(getProject()).rebuild(callback));
  }

  public List<CompilerMessage> compileModule(final Module module) {
    return runCompiler(callback -> CompilerManager.getInstance(getProject()).compile(module, callback));
  }

  public List<CompilerMessage> make(final CompileScope scope) {
    return runCompiler(callback -> CompilerManager.getInstance(getProject()).make(scope, callback));
  }

  public List<CompilerMessage> compileFiles(final VirtualFile... files) {
    return runCompiler(callback -> CompilerManager.getInstance(getProject()).compile(files, callback));
  }

  public @NotNull List<CompilerMessage> runCompiler(@NotNull Consumer<? super CompileStatusNotification> runnable) {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    ErrorReportingCallback callback = new ErrorReportingCallback(semaphore);
    PlatformTestUtil.saveProject(getProject(), false);
    CompilerTestUtil.saveApplicationSettings();
    CompilerTests.saveWorkspaceModelCaches(getProject());
    EdtTestUtil.runInEdtAndWait(() -> {
      // for now, a directory-based project is used for external storage
      if (!ProjectKt.isDirectoryBased(myProject)) {
        for (Module module : myModules) {
          Path ioFile = module.getModuleNioFile();
          assertTrue("File does not exist: " + ioFile, Files.exists(ioFile));
        }
      }

      PathMacros pathMacroManager = PathMacros.getInstance();
      Map<String, String> userMacros = pathMacroManager.getUserMacros();
      if (!userMacros.isEmpty()) {
        // require to be presented on disk
        Path macroFilePath = getPersistentStateComponentStorageLocation(pathMacroManager.getClass());
        assertNotNull(macroFilePath);
        if (!Files.exists(macroFilePath)) {
          String message = "File " + macroFilePath + " doesn't exist, but user macros defined: " + userMacros;
          // todo find out who deletes this file during tests
          LOG.warn(message);

          String fakeMacroName = "__remove_me__";
          IComponentStore appStore = IComponentStoreKt.getStateStore(ApplicationManager.getApplication());
          pathMacroManager.setMacro(fakeMacroName, fakeMacroName);
          appStore.saveComponent((PersistentStateComponent<?>)pathMacroManager);
          pathMacroManager.setMacro(fakeMacroName, null);
          appStore.saveComponent((PersistentStateComponent<?>)pathMacroManager);
          if (!Files.exists(macroFilePath)) {
            throw new AssertionError(message);
          }
        }
      }
      enableDebugLogging();
      runnable.consume(callback);
    });

    // tests run in awt
    while (!semaphore.waitFor(100)) {
      if (SwingUtilities.isEventDispatchThread()) {
        //noinspection TestOnlyProblems
        UIUtil.dispatchAllInvocationEvents();
      }
    }

    printBuildLog();
    callback.throwException();

    if (!((CompilerManagerImpl)CompilerManager.getInstance(getProject())).waitForExternalJavacToTerminate(1, TimeUnit.MINUTES)) {
      throw new RuntimeException("External javac thread is still running. Thread dump:" + ThreadDumper.dumpThreadsToString());
    }

    checkVfsNotLoadedForOutput();

    return callback.getMessages();
  }

  private void checkVfsNotLoadedForOutput() {
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
      if (extension != null) {
        for (String url : extension.getOutputRootUrls(true)) {
          VirtualFile root = VirtualFileManager.getInstance().refreshAndFindFileByUrl(url);
          IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects();
          if (root != null) {
            UsefulTestCase.assertEmpty(
              "VFS should not be loaded for output: that increases the number of VFS events and reindexing costs",
              ((NewVirtualFile)root).getCachedChildren());
          }
        }
      }
    }
  }

  public static void printBuildLog() {
    File logDirectory = BuildManager.getBuildLogDirectory();
    TestLoggerFactory.publishArtifactIfTestFails(logDirectory.toPath(), "build-log");
  }

  public static void enableDebugLogging()  {
    Path logDirectory = BuildManager.getBuildLogDirectory().toPath();
    try {
      NioFiles.deleteRecursively(logDirectory);
      Files.createDirectories(logDirectory);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    Properties properties = new Properties();
    try {
      try (InputStream config = LogSetup.readDefaultLogConfig()) {
        properties.load(config);
      }

      properties.setProperty(".level", "FINER");
      Path logFile = logDirectory.resolve(LogSetup.LOG_CONFIG_FILE_NAME);
      try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(logFile))) {
        properties.store(output, null);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static final class ErrorReportingCallback implements CompileStatusNotification {
    private final Semaphore mySemaphore;
    private Throwable myError;
    private final List<CompilerMessage> myMessages = new ArrayList<>();

    ErrorReportingCallback(@NotNull Semaphore semaphore) {
      mySemaphore = semaphore;
    }

    @Override
    public void finished(boolean aborted, int errors, int warnings, @NotNull final CompileContext compileContext) {
      try {
        for (CompilerMessageCategory category : CompilerMessageCategory.values()) {
          CompilerMessage[] messages = compileContext.getMessages(category);
          for (CompilerMessage message : messages) {
            String text = message.getMessage();
            if (category != CompilerMessageCategory.INFORMATION || !isSpamMessage(text)) {
              myMessages.add(message);
            }
          }
        }
        Assert.assertFalse("Code did not compile!", aborted);
      }
      catch (Throwable t) {
        myError = t;
      }
      finally {
        mySemaphore.up();
      }
    }

    private static boolean isSpamMessage(String text) {
      return text.contains("Build completed successfully in ") ||
             text.contains("used to compile") ||
             text.contains("illegal reflective") ||
             text.contains("Picked up") ||
             text.contains("consider reporting this to the maintainers") ||
             text.contains("Errors occurred while compiling module") ||
             text.startsWith("Using Groovy-Eclipse");
    }

    void throwException() {
      if (myError != null) {
        ExceptionUtil.rethrow(myError);
      }
    }

    @NotNull
    public List<CompilerMessage> getMessages() {
      return myMessages;
    }
  }
}
