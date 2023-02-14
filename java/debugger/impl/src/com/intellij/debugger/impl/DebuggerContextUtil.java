// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPass;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.SuspendManagerUtil;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class DebuggerContextUtil {
  public static void setStackFrame(final DebuggerStateManager manager, final StackFrameProxyImpl stackFrame) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final DebuggerContextImpl context = manager.getContext();

    final DebuggerSession session = context.getDebuggerSession();
    if (session != null) {
      session.getProcess().getManagerThread().schedule(new DebuggerCommandImpl(PrioritizedTask.Priority.HIGH) {
        @Override
        protected void action() {
          SuspendContextImpl threadSuspendContext =
            SuspendManagerUtil.findContextByThread(session.getProcess().getSuspendManager(), stackFrame.threadProxy());
          final DebuggerContextImpl newContext =
            DebuggerContextImpl.createDebuggerContext(session, threadSuspendContext, stackFrame.threadProxy(), stackFrame);
          newContext.initCaches();
          DebuggerInvocationUtil.swingInvokeLater(session.getProject(), () -> {
            manager.setState(newContext, session.getState(), DebuggerSession.Event.REFRESH, null);
            SourceCodeChecker.checkSource(newContext);
          });
        }
      });
    }
    else {
      manager.setState(DebuggerContextImpl.EMPTY_CONTEXT, DebuggerSession.State.DISPOSED, DebuggerSession.Event.REFRESH, null);
    }
  }

  public static void setThread(DebuggerStateManager contextManager, ThreadDescriptorImpl item) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final DebuggerSession session = contextManager.getContext().getDebuggerSession();
    final DebuggerContextImpl newContext =
      DebuggerContextImpl.createDebuggerContext(session, item.getSuspendContext(), item.getThreadReference(), null);

    contextManager.setState(newContext, session != null ? session.getState() : DebuggerSession.State.DISPOSED,
                            DebuggerSession.Event.CONTEXT, null);
  }

  @NotNull
  public static DebuggerContextImpl createDebuggerContext(@NotNull DebuggerSession session, SuspendContextImpl suspendContext) {
    return DebuggerContextImpl.createDebuggerContext(
      session, suspendContext, suspendContext != null ? suspendContext.getThread() : null, null);
  }

  public static SourcePosition findNearest(@NotNull DebuggerContextImpl context, @NotNull PsiElement psi, @NotNull PsiFile file) {
    if (psi instanceof PsiCompiledElement) {
      // it makes no sense to compute text range of compiled element
      return null;
    }
    final DebuggerSession session = context.getDebuggerSession();
    if (session != null) {
      return ProgressManager.getInstance().runProcess(() -> {
        try {
          final XDebugSession debugSession = session.getXDebugSession();
          if (debugSession != null) {
            final XSourcePosition position = debugSession.getCurrentPosition();
            Editor editor = ((FileEditorManagerImpl)FileEditorManager.getInstance(file.getProject())).getSelectedTextEditor(true);

            //final Editor editor = fileEditor instanceof TextEditorImpl ? ((TextEditorImpl)fileEditor).getEditor() : null;
            if (editor != null && position != null && position.getFile().equals(file.getOriginalFile().getVirtualFile())) {
              PsiMethod method = PsiTreeUtil.getParentOfType(PositionUtil.getContextElement(context), PsiMethod.class, false);
              final Collection<TextRange> ranges =
                IdentifierHighlighterPass.getUsages(psi, method != null ? method : file, false);
              final int breakPointLine = position.getLine();
              int bestLine = -1;
              int bestOffset = -1;
              int textOffset = method != null ? method.getTextOffset() : -1;
              for (TextRange range : ranges) {
                // skip comments
                if (range.getEndOffset() < textOffset) {
                  continue;
                }
                final int line = editor.offsetToLogicalPosition(range.getStartOffset()).line;
                if (line > bestLine && line < breakPointLine) {
                  bestLine = line;
                  bestOffset = range.getStartOffset();
                }
                else if (line == breakPointLine) {
                  bestOffset = range.getStartOffset();
                  break;
                }
              }
              if (bestOffset > -1) {
                return SourcePosition.createFromOffset(file, bestOffset);
              }
            }
          }
        }
        catch (Exception ignore) {
        }
        return null;
      }, new EmptyProgressIndicator());
    }
    return null;
  }
}
