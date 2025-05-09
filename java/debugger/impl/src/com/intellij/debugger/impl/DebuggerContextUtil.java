// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.codeInsight.daemon.impl.IdentifierHighlightingComputer;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.SuspendManagerUtil;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

public final class DebuggerContextUtil {
  public static void setStackFrame(final DebuggerStateManager manager, final StackFrameProxyImpl stackFrame) {
    ThreadingAssertions.assertEventDispatchThread();
    final DebuggerContextImpl context = manager.getContext();

    final DebuggerSession session = context.getDebuggerSession();
    if (session != null) {
      Objects.requireNonNull(context.getManagerThread()).schedule(new DebuggerCommandImpl(PrioritizedTask.Priority.HIGH) {
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
    ThreadingAssertions.assertEventDispatchThread();

    final DebuggerSession session = contextManager.getContext().getDebuggerSession();
    final DebuggerContextImpl newContext =
      DebuggerContextImpl.createDebuggerContext(session, item.getSuspendContext(), item.getThreadReference(), null);

    contextManager.setState(newContext, session != null ? session.getState() : DebuggerSession.State.DISPOSED,
                            DebuggerSession.Event.CONTEXT, null);
  }

  public static @NotNull DebuggerContextImpl createDebuggerContext(@NotNull DebuggerSession session, SuspendContextImpl suspendContext) {
    return DebuggerContextImpl.createDebuggerContext(
      session, suspendContext, suspendContext != null ? suspendContext.getThread() : null, null);
  }

  /**
   * Find position of the usage of the element {@code psi}, which is above and as close to the current position of debugger as possible.
   * The scope of the search is limited to the current executed method.
   */
  public static SourcePosition findNearest(@NotNull DebuggerContextImpl context, @NotNull PsiElement psi, @NotNull PsiFile file) {
    if (psi instanceof PsiCompiledElement) {
      // it makes no sense to compute text range of compiled element
      return null;
    }

    return findNearest(context, file, searchScope ->
      IdentifierHighlightingComputer.getUsages(psi, searchScope, false));
  }

  /**
   * Find position of the usage of the element {@code psi}, which is above and as close to the current position of debugger as possible.
   * The scope of the search is limited to the current executed method.
   */
  public static SourcePosition findNearest(@NotNull DebuggerContextImpl context, @NotNull PsiFile file, Function<PsiElement, Collection<TextRange>> findUsages) {
    final DebuggerSession session = context.getDebuggerSession();
    if (session == null) return null;

    try {
      final XDebugSession debugSession = session.getXDebugSession();
      if (debugSession == null) return null;

      final XSourcePosition position = debugSession.getCurrentPosition();
      Editor editor = FileEditorManager.getInstance(file.getProject()).getSelectedTextEditor(true);
      if (editor == null || position == null || !position.getFile().equals(file.getOriginalFile().getVirtualFile())) return null;

      PsiMethod method = PsiTreeUtil.getParentOfType(PositionUtil.getContextElement(context), PsiMethod.class, false);
      PsiElement searchScope = method != null ? method : file;
      final Collection<TextRange> ranges = findUsages.apply(searchScope);
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
    catch (Exception ignore) {
    }
    return null;
  }
}
