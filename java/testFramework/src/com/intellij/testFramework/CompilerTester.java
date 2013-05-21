/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.compiler.CompilerTestUtil;
import com.intellij.openapi.application.AccessToken;
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
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class CompilerTester {
  private final boolean myExternalMake;
  private final Module myModule;
  private TempDirTestFixture myMainOutput;

  public CompilerTester(boolean externalMake, Module module) throws Exception {
    myExternalMake = externalMake;
    myModule = module;
    myMainOutput = new TempDirTestFixtureImpl();
    myMainOutput.setUp();

    CompilerManagerImpl.testSetup();
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        //noinspection ConstantConditions
        CompilerProjectExtension.getInstance(getProject()).setCompilerOutputUrl(myMainOutput.findOrCreateDir("out").getUrl());
        if (myExternalMake) {
          CompilerTestUtil.enableExternalCompiler(getProject());
          ModuleRootModificationUtil.setModuleSdk(myModule, JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk());
        }
        else {
          CompilerTestUtil.disableExternalCompiler(getProject());
        }
      }
    }.execute();

  }

  public void tearDown() {
    if (myExternalMake) {
      CompilerTestUtil.disableExternalCompiler(getProject());
    }

    try {
      myMainOutput.tearDown();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    myMainOutput = null;
  }

  private Project getProject() {
    return myModule.getProject();
  }

  public void deleteClassFile(final String className) throws IOException {
    AccessToken token = WriteAction.start();
    try {
      if (myExternalMake) {
        //noinspection ConstantConditions
        touch(
          JavaPsiFacade.getInstance(getProject()).findClass(className, GlobalSearchScope.allScope(getProject())).getContainingFile().getVirtualFile());
      }
      else {
        //noinspection ConstantConditions
        findClassFile(className, myModule).delete(this);
      }
    }
    finally {
      token.finish();
    }
  }

  @Nullable
  public VirtualFile findClassFile(String className, Module module) {
    //noinspection ConstantConditions
    VirtualFile path = ModuleRootManager.getInstance(module).getModuleExtension(CompilerModuleExtension.class).getCompilerOutputPath();
    path.getChildren();
    assert path != null;
    path.refresh(false, true);
    return path.findChild(className.replace('.', '/') + ".class");
  }

  public void touch(VirtualFile file) throws IOException {
    file.setBinaryContent(file.contentsToByteArray(), -1, file.getTimeStamp() + 1);
    File ioFile = VfsUtil.virtualToIoFile(file);
    assert ioFile.setLastModified(ioFile.lastModified() - 100000);
    file.refresh(false, false);
  }

  public void setFileText(final PsiFile file, final String text) throws IOException {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          final VirtualFile virtualFile = file.getVirtualFile();
          VfsUtil.saveText(ObjectUtils.assertNotNull(virtualFile), text);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
    touch(file.getVirtualFile());
  }

  public void setFileName(final PsiFile file, final String name) {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        file.setName(name);
      }
    }.execute();
  }


  public List<CompilerMessage> make() {
    return runCompiler(new Consumer<ErrorReportingCallback>() {
      @Override
      public void consume(ErrorReportingCallback callback) {
        CompilerManager.getInstance(getProject()).make(callback);
      }
    });
  }

  public List<CompilerMessage> rebuild() {
    return runCompiler(new Consumer<ErrorReportingCallback>() {
      @Override
      public void consume(ErrorReportingCallback callback) {
        CompilerManager.getInstance(getProject()).rebuild(callback);
      }
    });
  }

  public List<CompilerMessage> compileModule(final Module module) {
    return runCompiler(new Consumer<ErrorReportingCallback>() {
      @Override
      public void consume(ErrorReportingCallback callback) {
        CompilerManager.getInstance(getProject()).compile(module, callback);
      }
    });
  }

  public List<CompilerMessage> compileFiles(final VirtualFile... files) {
    return runCompiler(new Consumer<ErrorReportingCallback>() {
      @Override
      public void consume(ErrorReportingCallback callback) {
        CompilerManager.getInstance(getProject()).compile(files, callback);
      }
    });
  }

  private List<CompilerMessage> runCompiler(final Consumer<ErrorReportingCallback> runnable) {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final ErrorReportingCallback callback = new ErrorReportingCallback(semaphore);
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          if (myExternalMake) {
            getProject().save();
            CompilerTestUtil.saveApplicationSettings();
            File ioFile = VfsUtil.virtualToIoFile(myModule.getModuleFile());
            if (!ioFile.exists()) {
              getProject().save();
              assert ioFile.exists() : "File does not exist: " + ioFile.getPath();
            }
          }
          runnable.consume(callback);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });

    //tests run in awt
    while (!semaphore.waitFor(100)) {
      if (SwingUtilities.isEventDispatchThread()) {
        UIUtil.dispatchAllInvocationEvents();
      }
    }
    callback.throwException();
    return callback.getMessages();
  }

  private static class ErrorReportingCallback implements CompileStatusNotification {
    private final Semaphore mySemaphore;
    private Throwable myError;
    private final List<CompilerMessage> myMessages = new ArrayList<CompilerMessage>();

    public ErrorReportingCallback(Semaphore semaphore) {
      mySemaphore = semaphore;
    }

    @Override
    public void finished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
      try {
        for (CompilerMessageCategory category : CompilerMessageCategory.values()) {
          CompilerMessage[] messages = compileContext.getMessages(category);
          for (CompilerMessage message : messages) {
            final String text = message.getMessage();
            if (category != CompilerMessageCategory.INFORMATION || !(text.startsWith("Compilation completed successfully") || text.startsWith("Using javac"))) {
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
