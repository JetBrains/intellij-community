// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.NoDataException;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.PositionManagerImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.execution.filters.LineNumbersMapping;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.navigation.NavigationItem;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.sun.jdi.*;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class SourceCodeChecker {
  private static final Logger LOG = Logger.getInstance(SourceCodeChecker.class);

  private SourceCodeChecker() {
  }

  public static void checkSource(DebuggerContextImpl debuggerContext) {
    if (!Registry.is("debugger.check.source")) {
      return;
    }
    SuspendContextImpl suspendContext = debuggerContext.getSuspendContext();
    if (suspendContext == null) {
      return;
    }
    suspendContext.getDebugProcess().getManagerThread().schedule(new SuspendContextCommandImpl(suspendContext) {

      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        try {
          StackFrameProxyImpl frameProxy = debuggerContext.getFrameProxy();
          if (frameProxy == null) {
            return;
          }
          Location location = frameProxy.location();
          check(location, debuggerContext.getSourcePosition(), suspendContext.getDebugProcess().getProject());
          //checkAllClasses(debuggerContext);
        }
        catch (EvaluateException e) {
          LOG.info(e);
        }
      }
    });
  }

  private static CompletableFuture<ThreeState> check(Location location, SourcePosition position, Project project) {
    Method method = DebuggerUtilsEx.getMethod(location);
    // for now skip constructors, bridges, lambdas etc.
    if (method == null ||
        method.isConstructor() ||
        method.isSynthetic() ||
        method.isBridge() ||
        method.isStaticInitializer() ||
        (method.declaringType() instanceof ClassType && ((ClassType)method.declaringType()).isEnum()) ||
        DebuggerUtilsEx.isLambda(method)) {
      return CompletableFuture.completedFuture(ThreeState.UNSURE);
    }
    return DebuggerUtilsAsync.allLineLocations(method).thenApply(locations -> {
      if (ContainerUtil.isEmpty(locations)) {
        return ThreeState.UNSURE;
      }
      if (position != null) {
        return ReadAction.compute(() -> {
          PsiFile psiFile = position.getFile();
          if (!psiFile.getLanguage().isKindOf(JavaLanguage.INSTANCE)) { // only for java for now
            return ThreeState.UNSURE;
          }
          Document document = psiFile.getViewProvider().getDocument();
          if (document == null) {
            return ThreeState.UNSURE;
          }
          boolean res = false;
          PsiElement psiMethod = DebuggerUtilsEx.getContainingMethod(position);
          if (psiMethod != null) {
            TextRange range = psiMethod.getTextRange();
            if (psiMethod instanceof PsiDocCommentOwner) {
              PsiDocComment comment = ((PsiDocCommentOwner)psiMethod).getDocComment();
              if (comment != null) {
                range = new TextRange(comment.getTextRange().getEndOffset() + 1, range.getEndOffset());
              }
            }
            int startLine = document.getLineNumber(range.getStartOffset()) + 1;
            int endLine = document.getLineNumber(range.getEndOffset()) + 1;
            res = getLinesStream(locations, psiFile).allMatch(line -> startLine <= line && line <= endLine);
            if (!res) {
              LOG.debug("Source check failed: Method " + method.name() + ", source: " + ((NavigationItem)psiMethod).getName() +
                        "\nLines: " + getLinesStream(locations, psiFile).joining(", ") +
                        "\nExpected range: " + startLine + "-" + endLine
              );
            }
          }
          else {
            LOG.debug("Source check failed: method " + method.name() + " not found in sources");
          }
          if (!res) {
            VirtualFile virtualFile = position.getFile().getVirtualFile();
            if (virtualFile != null) {
              AppUIUtil.invokeOnEdt(() -> {
                if (!project.isDisposed()) {
                  FileEditor editor = FileEditorManager.getInstance(project).getSelectedEditor(virtualFile);
                  if (editor instanceof TextEditor) {
                    HintManager.getInstance().showErrorHint(((TextEditor)editor).getEditor(),
                                                            JavaDebuggerBundle.message("warning.source.code.not.match"));
                  }
                  else {
                    XDebuggerManagerImpl.getNotificationGroup()
                      .createNotification(JavaDebuggerBundle.message("warning.source.code.not.match"), NotificationType.WARNING)
                      .notify(project);
                  }
                }
              });
            }
            return ThreeState.NO;
          }
          return ThreeState.YES;
        });
      }
      return ThreeState.YES;
    });
  }

  private static IntStreamEx getLinesStream(List<Location> locations, PsiFile psiFile) {
    IntStreamEx stream = StreamEx.of(locations).mapToInt(Location::lineNumber);
    if (psiFile instanceof PsiCompiledFile) {
      stream = stream.map(line -> DebuggerUtilsEx.bytecodeToSourceLine(psiFile, line) + 1);
    }
    return stream.filter(line -> line > 0);
  }

  @TestOnly
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void checkAllClasses(DebuggerContextImpl debuggerContext) {
    DebugProcessImpl process = debuggerContext.getDebugProcess();
    @SuppressWarnings("ConstantConditions")
    VirtualMachine machine = process.getVirtualMachineProxy().getVirtualMachine();
    PositionManagerImpl positionManager = new PositionManagerImpl(process); // only default position manager for now
    List<ReferenceType> types = machine.allClasses();
    System.out.println("Checking " + types.size() + " classes");
    int i = 0;
    for (ReferenceType type : types) {
      i++;
      try {
        for (Location loc : DebuggerUtilsAsync.allLineLocationsSync(type)) {
          SourcePosition position =
            ReadAction.compute(() -> {
              try {
                return positionManager.getSourcePosition(loc);
              }
              catch (NoDataException ignore) {
                return null;
              }
            });
          if (position == null) {
            continue;
          }
          if (position.getFile() instanceof PsiCompiledFile) {
            VirtualFile file = position.getFile().getVirtualFile();
            if (file == null || file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY) == null) {
              break; // no mapping - skip the whole file
            }
            if (DebuggerUtilsEx.bytecodeToSourceLine(position.getFile(), loc.lineNumber()) == -1) {
              continue;
            }
          }
          check(loc, position, process.getProject()).thenAccept(res -> {
            if (res == ThreeState.NO) {
              System.out.println("failed " + type);
            }
          });
        }
      }
      catch (AbsentInformationException ignored) {
      }
    }
    System.out.println("Done checking");
  }
}
