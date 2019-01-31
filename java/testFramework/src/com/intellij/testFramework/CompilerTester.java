// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.compiler.CompilerTestUtil;
import com.intellij.compiler.server.BuildManager;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.io.FileTreePrinterKt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.cmdline.LogSetup;
import org.jetbrains.jps.model.serialization.JpsGlobalLoader;
import org.junit.Assert;

import javax.swing.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author peter
 */
public class CompilerTester {
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

  public CompilerTester(@NotNull Project project, @NotNull List<? extends Module> modules, @Nullable Disposable disposable) throws Exception {
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
    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      Objects.requireNonNull(CompilerProjectExtension.getInstance(getProject())).setCompilerOutputUrl(myMainOutput.findOrCreateDir("out").getUrl());
      if (!myModules.isEmpty()) {
        JavaAwareProjectJdkTableImpl projectJdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx();
        for (Module module : myModules) {
          ModuleRootModificationUtil.setModuleSdk(module, projectJdkTable.getInternalJdk());
        }
      }
    });
  }

  public void tearDown() {
    try {
      new RunAll(
        () -> CompilerTestUtil.disableExternalCompiler(getProject()),
        () -> myMainOutput.tearDown()
      ).run();
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
  public VirtualFile findClassFile(String className, Module module) {
    VirtualFile path = ModuleRootManager.getInstance(module).getModuleExtension(CompilerModuleExtension.class).getCompilerOutputPath();
    assert path != null;
    path.getChildren();
    path.refresh(false, true);
    return path.findFileByRelativePath(className.replace('.', '/') + ".class");
  }

  public void touch(final VirtualFile file) throws IOException {
    WriteAction.runAndWait(() -> {
      file.setBinaryContent(file.contentsToByteArray(), -1, file.getTimeStamp() + 1);
      File ioFile = VfsUtilCore.virtualToIoFile(file);
      assert ioFile.setLastModified(ioFile.lastModified() - 100000);
      file.refresh(false, false);
    });
  }

  public void setFileText(final PsiFile file, final String text) throws IOException {
    WriteAction.runAndWait(() -> {
      final VirtualFile virtualFile = file.getVirtualFile();
      VfsUtil.saveText(ObjectUtils.assertNotNull(virtualFile), text);
    });
    touch(file.getVirtualFile());
  }

  public void setFileName(final PsiFile file, final String name) {
    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      file.setName(name);
    });
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

  @NotNull
  public List<CompilerMessage> runCompiler(@NotNull Consumer<? super CompileStatusNotification> runnable) {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    final ErrorReportingCallback callback = new ErrorReportingCallback(semaphore);
    EdtTestUtil.runInEdtAndWait(() -> {
      refreshVfs(getProject().getProjectFilePath());
      for (Module module : myModules) {
        refreshVfs(module.getModuleFilePath());
      }

      PlatformTestUtil.saveProject(getProject(), false);
      CompilerTestUtil.saveApplicationSettings();
      // for now directory based project is used for external storage
      if (!ProjectKt.isDirectoryBased(myProject)) {
        for (Module module : myModules) {
          Path ioFile = Paths.get(module.getModuleFilePath());
          if (!Files.exists(ioFile)) {
            getProject().save();
            assert Files.exists(ioFile) : "File does not exist: " + ioFile.toString();
          }
        }
      }

      PathMacros pathMacroManager = PathMacros.getInstance();
      Map<String, String> userMacros = pathMacroManager.getUserMacros();
      if (!userMacros.isEmpty()) {
        // require to be presented on disk
        Path configDir = Paths.get(PathManager.getConfigPath());
        Path macroFilePath = configDir.resolve("options").resolve(JpsGlobalLoader.PathVariablesSerializer.STORAGE_FILE_NAME);
        if (!Files.exists(macroFilePath)) {
          String message = "File " + macroFilePath + " doesn't exist, but user macros defined: " + userMacros +
                           "\n\n File listing:" + FileTreePrinterKt.getDirectoryTree(configDir);
          // todo find out who deletes this file during tests
          LOG.warn(message);

          String fakeMacroName = "__remove_me__";
          IComponentStore applicationStore = CompilerTestUtil.getApplicationStore();
          pathMacroManager.setMacro(fakeMacroName, fakeMacroName);
          applicationStore.saveComponent((PersistentStateComponent<?>)pathMacroManager);
          pathMacroManager.removeMacro(fakeMacroName);
          applicationStore.saveComponent((PersistentStateComponent<?>)pathMacroManager);
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

    return callback.getMessages();
  }

  public static void printBuildLog() {
    File logDirectory = BuildManager.getBuildLogDirectory();
    File[] files = logDirectory.listFiles(file -> file.getName().endsWith(".log"));
    if (files == null || files.length == 0) {
      LOG.debug("No *.log files in " + logDirectory + " after build");
      return;
    }

    Arrays.sort(files, Comparator.comparing(File::getName));
    for (File file : files) {
      LOG.debug(file.getName() + ":");
      try {
        List<String> lines = FileUtil.loadLines(file);
        for (String line : lines) {
          LOG.debug(line);
        }
      }
      catch (IOException e) {
        LOG.debug("Failed to load contents: " + e.getMessage());
      }
    }
  }

  public static void enableDebugLogging() throws IOException {
    File logDirectory = BuildManager.getBuildLogDirectory();
    FileUtil.delete(logDirectory);
    FileUtil.createDirectory(logDirectory);
    Properties properties = new Properties();
    try (InputStream config = LogSetup.readDefaultLogConfig()) {
      properties.load(config);
    }

    properties.setProperty("log4j.rootLogger", "debug, file");
    File logFile = new File(logDirectory, LogSetup.LOG_CONFIG_FILE_NAME);
    try (OutputStream output = new BufferedOutputStream(new FileOutputStream(logFile))) {
      properties.store(output, null);
    }
  }

  private static void refreshVfs(String path) {
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(path));
    if (vFile != null) {
      vFile.refresh(false, false);
    }
  }

  private static class ErrorReportingCallback implements CompileStatusNotification {
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
      return text.contains(CompilerBundle.message("status.compilation.completed.successfully")) ||
             text.contains("used to compile") ||
             text.contains("illegal reflective") ||
             text.contains("Picked up") ||
             text.contains("consider reporting this to the maintainers") ||
             text.startsWith("Using Groovy-Eclipse");
    }

    void throwException() {
      if (myError != null) {
        ExceptionUtilRt.rethrow(myError);
      }
    }

    @NotNull
    public List<CompilerMessage> getMessages() {
      return myMessages;
    }
  }
}
