// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.backwardRefs;

import com.intellij.compiler.CompilerReferenceService;
import com.intellij.compiler.PwaService;
import com.intellij.compiler.server.BuildManager;
import com.intellij.compiler.server.BuildManagerListener;
import com.intellij.lang.jvm.JvmClass;
import com.intellij.lang.jvm.JvmElement;
import com.intellij.lang.jvm.JvmField;
import com.intellij.lang.jvm.JvmMethod;
import com.intellij.lang.jvm.util.JvmClassUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerTopics;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.jps.backwardRefs.pwa.ClassFileSymbol;
import org.jetbrains.jps.backwardRefs.pwa.PwaIndex;
import org.jetbrains.jps.backwardRefs.pwa.PwaIndices;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PwaServiceImpl extends PwaService {
  private volatile PwaIndex myIndex;
  protected final LongAdder myCompilationCount = new LongAdder();
  protected final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();
  protected final Lock myReadDataLock = myLock.readLock();
  protected final Lock myOpenCloseLock = myLock.writeLock();
  protected final DirtyScopeHolder myDirtyScopeHolder;
  protected int myActiveBuilds = 0;

  public PwaServiceImpl(Project project, FileDocumentManager fileDocumentManager,
                                      PsiDocumentManager psiDocumentManager) {
    super(project);
    myDirtyScopeHolder = new DirtyScopeHolder(
      ProjectFileIndex.getInstance(project), project,  fileDocumentManager,psiDocumentManager,this,  Collections
      .emptySet(), (connection, strings) -> {});
  }


  @Override
  public void projectOpened() {
    if (CompilerReferenceService.isEnabled()) {
      MessageBusConnection connection = myProject.getMessageBus().connect(myProject);
      connection.subscribe(BuildManagerListener.TOPIC, new BuildManagerListener() {
        @Override
        public void buildStarted(Project project, UUID sessionId, boolean isAutomake) {
          if (project == myProject) {
            closeReaderIfNeed(CompilerReferenceServiceBase.IndexCloseReason.COMPILATION_STARTED);
          }
        }
      });

      connection.subscribe(CompilerTopics.COMPILATION_STATUS, new CompilationStatusListener() {
        @Override
        public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
          compilationFinished(compileContext);
        }

        @Override
        public void automakeCompilationFinished(int errors, int warnings, CompileContext compileContext) {
          compilationFinished(compileContext);
        }

        private void compilationFinished(CompileContext context) {
          if (context.getProject() == myProject) {
            Runnable compilationFinished = () -> {
              final Module[] compilationModules = ReadAction.compute(() -> {
                if (myProject.isDisposed()) return null;
                return context.getCompileScope().getAffectedModules();
              });
              if (compilationModules == null) return;
              openReaderIfNeed(CompilerReferenceServiceBase.IndexOpenReason.COMPILATION_FINISHED);
            };
            executeOnBuildThread(compilationFinished);
          }
        }
      });
    }
  }


  protected void closeReaderIfNeed(CompilerReferenceServiceBase.IndexCloseReason reason) {
    myOpenCloseLock.lock();
    try {
      if (reason == CompilerReferenceServiceBase.IndexCloseReason.COMPILATION_STARTED) {
        myActiveBuilds++;
        myDirtyScopeHolder.compilerActivityStarted();
      }
      if (myIndex != null) {
        myIndex.close();
        myIndex = null;
      }
    } finally {
      myOpenCloseLock.unlock();
    }
  }

  protected void openReaderIfNeed(CompilerReferenceServiceBase.IndexOpenReason reason) {
    myCompilationCount.increment();
    myOpenCloseLock.lock();
    try {
      try {
        switch (reason) {
          case UP_TO_DATE_CACHE:
            myDirtyScopeHolder.upToDateChecked(true);
            break;
          case COMPILATION_FINISHED:
            myDirtyScopeHolder.compilerActivityFinished();
        }
      }
      catch (RuntimeException e) {
        --myActiveBuilds;
        throw e;
      }
    }
    finally {
      myOpenCloseLock.unlock();
    }
  }

  protected static void executeOnBuildThread(Runnable compilationFinished) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      compilationFinished.run();
    } else {
      BuildManager.getInstance().runCommand(compilationFinished);
    }
  }

  @Override
  public void projectClosed() {
    closeReaderIfNeed(CompilerReferenceServiceBase.IndexCloseReason.PROJECT_CLOSED);
  }

  @Override
  public boolean isBytecodeUsed(JvmElement element) {
    try {
      return myIndex.get(PwaIndices.BACK_USAGES).getData(asSymbol(element)).forEach((id, value) -> true);
    }
    catch (StorageException e) {
      throw new RuntimeException(e);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private ClassFileSymbol asSymbol(JvmElement element) throws IOException {
    if (element instanceof JvmClass) {
      String name = JvmClassUtil.getJvmClassName((JvmClass)element);
      int id = myIndex.getByteSeqEum().tryEnumerate(name);
      return new ClassFileSymbol.Clazz(id);
    }
    else if (element instanceof JvmField) {
      String className = JvmClassUtil.getJvmClassName(((JvmField)element).getContainingClass());
      String name = ((JvmField)element).getName();
      int id = myIndex.getByteSeqEum().tryEnumerate(name);
      int classId = myIndex.getByteSeqEum().tryEnumerate(className);
      return new ClassFileSymbol.Field(id, classId);
    }
    else if (element instanceof JvmMethod) {
      String className = JvmClassUtil.getJvmClassName(((JvmField)element).getContainingClass());
      String name = ((JvmField)element).getName();
      int id = myIndex.getByteSeqEum().tryEnumerate(name);
      int classId = myIndex.getByteSeqEum().tryEnumerate(className);
      return new ClassFileSymbol.Method(id, classId, ((JvmMethod)element).getParameters().length);
    }
    throw new AssertionError(element.getClass());
  }
}
