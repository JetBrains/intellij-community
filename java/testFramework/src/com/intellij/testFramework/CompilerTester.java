/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.testFramework;

import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.compiler.CompilerTestUtil;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author peter
 */
public class CompilerTester {
  private Project myProject;
  private List<Module> myModules;
  private TempDirTestFixture myMainOutput;

  public CompilerTester(Module module) throws Exception {
    this(module.getProject(), Collections.singletonList(module));
  }

  public CompilerTester(Project project, List<Module> modules) throws Exception {
    myProject = project;
    myModules = modules;
    myMainOutput = new TempDirTestFixtureImpl();
    myMainOutput.setUp();

    CompilerTestUtil.enableExternalCompiler();
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        //noinspection ConstantConditions
        CompilerProjectExtension.getInstance(getProject()).setCompilerOutputUrl(myMainOutput.findOrCreateDir("out").getUrl());
        for (Module module : myModules) {
          ModuleRootModificationUtil.setModuleSdk(module, JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk());
        }
      }
    }.execute();
  }

  public void tearDown() {
    CompilerTestUtil.disableExternalCompiler(getProject());

    try {
      myMainOutput.tearDown();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    finally {
      myMainOutput = null;
      myModules = null;
    }
  }

  private Project getProject() {
    return myProject;
  }

  public void deleteClassFile(final String className) throws IOException {
    WriteAction.run(() -> {
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
    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        file.setBinaryContent(file.contentsToByteArray(), -1, file.getTimeStamp() + 1);
        File ioFile = VfsUtilCore.virtualToIoFile(file);
        assert ioFile.setLastModified(ioFile.lastModified() - 100000);
        file.refresh(false, false);
      }
    }.execute().throwException();
  }

  public void setFileText(final PsiFile file, final String text) throws IOException {
    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        final VirtualFile virtualFile = file.getVirtualFile();
        VfsUtil.saveText(ObjectUtils.assertNotNull(virtualFile), text);
      }
    }.execute().throwException();
    touch(file.getVirtualFile());
  }

  public void setFileName(final PsiFile file, final String name) {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        file.setName(name);
      }
    }.execute();
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

  public List<CompilerMessage> runCompiler(final Consumer<CompileStatusNotification> runnable) {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    final ErrorReportingCallback callback = new ErrorReportingCallback(semaphore);
    EdtTestUtil.runInEdtAndWait(() -> {
      refreshVfs(getProject().getProjectFilePath());
      for (Module module : myModules) {
        refreshVfs(module.getModuleFilePath());
      }

      PlatformTestUtil.saveProject(getProject());
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
      runnable.consume(callback);
    });

    //tests run in awt
    while (!semaphore.waitFor(100)) {
      if (SwingUtilities.isEventDispatchThread()) {
        //noinspection TestOnlyProblems
        UIUtil.dispatchAllInvocationEvents();
      }
    }

    callback.throwException();

    if (!((CompilerManagerImpl)CompilerManager.getInstance(getProject())).waitForExternalJavacToTerminate(1, TimeUnit.MINUTES)) {
      throw new RuntimeException("External javac thread is still running. Thread dump:" + ThreadDumper.dumpThreadsToString());
    }

    return callback.getMessages();
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

    ErrorReportingCallback(Semaphore semaphore) {
      mySemaphore = semaphore;
    }

    @Override
    public void finished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
      try {
        for (CompilerMessageCategory category : CompilerMessageCategory.values()) {
          CompilerMessage[] messages = compileContext.getMessages(category);
          for (CompilerMessage message : messages) {
            final String text = message.getMessage();
            if (category != CompilerMessageCategory.INFORMATION ||
                !(text.contains("Compilation completed successfully") ||
                  text.contains("used to compile") ||
                  text.startsWith("Using Groovy-Eclipse"))) {
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

    void throwException() {
      if (myError != null) {
        throw new RuntimeException(myError);
      }
    }

    public List<CompilerMessage> getMessages() {
      return myMessages;
    }
  }


}
