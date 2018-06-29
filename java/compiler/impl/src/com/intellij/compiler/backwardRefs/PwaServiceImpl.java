// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.backwardRefs;

import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer;
import com.intellij.compiler.CompilerReferenceService;
import com.intellij.compiler.PwaService;
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase;
import com.intellij.compiler.backwardRefs.DirtyScopeHolder;
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
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.JBColor;
import com.intellij.util.IconUtil;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.indexing.impl.MapIndexStorage;
import com.intellij.util.indexing.impl.MapReduceIndex;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.jps.backwardRefs.NameEnumerator;
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex;
import org.jetbrains.jps.backwardRefs.pwa.ClassFileSymbol;
import org.jetbrains.jps.backwardRefs.pwa.PwaIndex;
import org.jetbrains.jps.backwardRefs.pwa.PwaIndices;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PwaServiceImpl extends PwaService {

  enum IndexStatus { NOT_READY, NOT_EXIST, READY, COMPILING }

  private volatile PwaIndex myIndex;
  private volatile BytecodeGraph myGraph;

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

        File buildDir = BuildManager.getInstance().getProjectSystemDirectory(myProject);

        if (buildDir == null
            || !CompilerReferenceIndex.exists(buildDir)
            || CompilerReferenceIndex.versionDiffers(buildDir, PwaIndices.VERSION)) {
          return;
        }
        myIndex = new PwaIndex(buildDir, true);
        resetGraph();
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
    if (myIndex == null) return true;
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
      String className = JvmClassUtil.getJvmClassName(((JvmMethod)element).getContainingClass());
      String name = ((JvmMethod)element).getName();
      int id = myIndex.getByteSeqEum().tryEnumerate(name);
      int classId = myIndex.getByteSeqEum().tryEnumerate(className);
      return new ClassFileSymbol.Method(id, classId, ((JvmMethod)element).getParameters().length);
    }
    throw new AssertionError(element.getClass());
  }

  private void resetGraph() {
    int mainMethodName;
    NameEnumerator eum = myIndex.getByteSeqEum();
    try {
      mainMethodName = eum.enumerate("main");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    myGraph = new BytecodeGraph(mainMethodName);

    try {
      ((MapIndexStorage<ClassFileSymbol, Void>)((MapReduceIndex)myIndex.get(PwaIndices.DEF)).getStorage()).processKeys(symbol -> {
        JvmNode node = new JvmNode(symbol);
        myGraph.addNode(node);
        return true;
      });


      ((MapIndexStorage<ClassFileSymbol, ClassFileSymbol>)((MapReduceIndex)myIndex.get(PwaIndices.BACK_USAGES)).getStorage()).processKeys(symbol -> {
        JvmNode node = myGraph.getNodeFromSources(symbol);
        if (node != null) {
          try {
            myIndex.get(PwaIndices.BACK_USAGES).getData(symbol).forEach((id, value) -> {
              for (ClassFileSymbol v : value) {
                JvmNode usagePlace = myGraph.getNodeFromSources(v);
                if (usagePlace != null) {
                  node.usedIn(usagePlace);
                }
                else {
                  throw new IllegalStateException();
                }
              }

              return true;
            });
          }
          catch (StorageException e) {
            throw new RuntimeException(e);
          }
        }
        return true;
      });
      myGraph.graphBuilt();
      if (myGraph.isSomeCodeUnused()) {
        String text = "<html><b>Unused declaration problems:</b><br>";
        for (JvmNode node : myGraph.getUnusedNodes()) {
          ClassFileSymbol symbol = node.getSymbol();
          String name = eum.getName(symbol.name);
          if (symbol instanceof ClassFileSymbol.Method) {
            String ccName = eum.getName(((ClassFileSymbol.Method)symbol).containingClass);
            text += StringUtil.getShortName(ccName) + "." + name + "()" ;
          }
          else if (symbol instanceof ClassFileSymbol.Clazz) {
            text += StringUtil.getShortName(name);
          }
          else if (symbol instanceof ClassFileSymbol.Field) {
            String ccName = eum.getName(((ClassFileSymbol.Field)symbol).containingClass);
            text += StringUtil.getShortName(ccName) + "." + name;
          }
          text += "<br>";
        }


        String finalText = text;
        ReadAction.run(() -> {

          for (FileEditor editor : FileEditorManager.getInstance(myProject).getAllEditors()) {
            if (editor instanceof TextEditor) {
              MarkupModel model = ((TextEditor)editor).getEditor().getMarkupModel();
              if (model instanceof EditorMarkupModel) {
                ErrorStripeRenderer renderer = ((EditorMarkupModel)model).getErrorStripeRenderer();
                if (renderer instanceof TrafficLightRenderer) {
                  ((TrafficLightRenderer)renderer).pwaIcon = new ColorIcon(14, JBColor.lightGray);
                  ((TrafficLightRenderer)renderer).pwaText = finalText;
                  UIUtil.invokeLaterIfNeeded(() -> {
                    editor.getComponent().repaint();
                  });
                }
              }
            }
          }
        });
      } else {
        ReadAction.run(() -> {
          for (FileEditor editor : FileEditorManager.getInstance(myProject).getAllEditors()) {
            if (editor instanceof TextEditor) {
              MarkupModel model = ((TextEditor)editor).getEditor().getMarkupModel();
              if (model instanceof EditorMarkupModel) {
                ErrorStripeRenderer renderer = ((EditorMarkupModel)model).getErrorStripeRenderer();
                if (renderer instanceof TrafficLightRenderer) {
                  ((TrafficLightRenderer)renderer).pwaIcon = null;
                  ((TrafficLightRenderer)renderer).pwaText = "";
                  UIUtil.invokeLaterIfNeeded(() -> {
                    editor.getComponent().repaint();
                  });
                }
              }
            }
          }
        });
      }

    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

}
