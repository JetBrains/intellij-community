// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.attach;

import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.presentation.PresentationRenderer;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.actions.JavaDebuggerActionsCollector;
import com.intellij.debugger.statistics.DebuggerStatistics;
import com.intellij.debugger.ui.breakpoints.ExceptionBreakpoint;
import com.intellij.debugger.ui.breakpoints.JavaExceptionBreakpointType;
import com.intellij.execution.filters.Filter.ResultItem;
import com.intellij.execution.filters.JvmExceptionOccurrenceFilter;
import com.intellij.execution.impl.InlayProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointsDialogFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaExceptionBreakpointProperties;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class JavaDebuggerAddExceptionBreakpointFilter implements JvmExceptionOccurrenceFilter {
  @Override
  public @Nullable ResultItem applyFilter(@NotNull String exceptionClassName,
                                          @NotNull List<PsiClass> classes,
                                          int exceptionStartOffset) {
    return new CreateExceptionBreakpointResult(exceptionStartOffset, exceptionStartOffset + exceptionClassName.length(),
                                               exceptionClassName);
  }

  private static class CreateExceptionBreakpointResult extends ResultItem implements InlayProvider {
    private final String myExceptionFqn;

    CreateExceptionBreakpointResult(int highlightStartOffset, int highlightEndOffset, String exceptionFqn) {
      super(highlightStartOffset, highlightEndOffset, null);
      myExceptionFqn = exceptionFqn;
    }

    @Override
    public EditorCustomElementRenderer createInlayRenderer(Editor editor) {
      PresentationFactory factory = new PresentationFactory(editor);
      DebuggerStatistics.logThreadDumpTriggerException(editor.getProject(), myExceptionFqn);
      InlayPresentation presentation =
        factory.referenceOnHover(factory.roundWithBackground(factory.smallText("Create breakpoint")), (event, point) -> {
          JavaDebuggerActionsCollector.createExceptionBreakpointInlay.log();
          Project project = editor.getProject();
          Collection<? extends XBreakpoint<JavaExceptionBreakpointProperties>> exceptionBreakpoints =
            XDebuggerManager.getInstance(project).getBreakpointManager().getBreakpoints(JavaExceptionBreakpointType.class);
          XBreakpoint<JavaExceptionBreakpointProperties> breakpoint =
            ContainerUtil.find(exceptionBreakpoints, b -> Objects.equals(myExceptionFqn, b.getProperties().myQualifiedName));
          if (breakpoint == null) {
            ExceptionBreakpoint ebpt = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager()
              .addExceptionBreakpoint(myExceptionFqn);
            if (ebpt != null) {
              breakpoint = ebpt.getXBreakpoint();
            }
          }
          if (breakpoint != null) {
            BreakpointsDialogFactory.getInstance(project).showDialog(breakpoint);
          }
        });
      return new PresentationRenderer(presentation);
    }
  }
}
