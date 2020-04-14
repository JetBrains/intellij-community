// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.attach;

import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.presentation.PresentationRenderer;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.ui.breakpoints.ExceptionBreakpoint;
import com.intellij.debugger.ui.breakpoints.JavaExceptionBreakpointType;
import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.impl.InlayProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointsDialogFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaExceptionBreakpointProperties;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaDebuggerConsoleFilterProvider implements ConsoleFilterProvider {
  @Override
  public Filter @NotNull [] getDefaultFilters(@NotNull Project project) {
    return new Filter[]{new JavaDebuggerAttachFilter(), new JavaDebuggerExceptionFilter()};
  }

  private static class JavaDebuggerAttachFilter implements Filter {
    static final Pattern PATTERN = Pattern.compile("Listening for transport (\\S+) at address: (\\S+)");

    @Override
    public @Nullable Result applyFilter(@NotNull String line, int entireLength) {
      Matcher matcher = PATTERN.matcher(line);
      if (!matcher.find()) {
        return null;
      }
      String transport = matcher.group(1);
      String address = matcher.group(2);
      int start = entireLength - line.length();

      // to trick the code unwrapping single results in com.intellij.execution.filters.CompositeFilter#createFinalResult
      return new Result(Arrays.asList(
        new AttachInlayResult(start + matcher.start(), start + matcher.end(), transport, address),
        new ResultItem(0, 0, null)));
    }
  }

  private static class AttachInlayResult extends Filter.ResultItem implements InlayProvider {
    private final String myTransport;
    private final String myAddress;

    AttachInlayResult(int highlightStartOffset, int highlightEndOffset, String transport, String address) {
      super(highlightStartOffset, highlightEndOffset, null);
      myTransport = transport;
      myAddress = address;
    }

    @Override
    public EditorCustomElementRenderer createInlayRenderer(Editor editor) {
      PresentationFactory factory = new PresentationFactory((EditorImpl)editor);
      InlayPresentation presentation = factory.referenceOnHover(factory.roundWithBackground(factory.smallText("Attach debugger")),
                                                                (event, point) -> {
                                                                  JavaAttachDebuggerProvider
                                                                    .attach(myTransport, myAddress, editor.getProject());
                                                                });
      return new PresentationRenderer(presentation);
    }
  }

  private static class JavaDebuggerExceptionFilter implements Filter {
    static final Pattern PATTERN = Pattern.compile("Exception in thread \"(.+)\" (\\S+)");

    @Override
    public @Nullable Result applyFilter(@NotNull String line, int entireLength) {
      Matcher matcher = PATTERN.matcher(line);
      if (!matcher.find()) {
        return null;
      }
      String exceptionFqn = matcher.group(2);
      int start = entireLength - line.length();

      // to trick the code unwrapping single results in com.intellij.execution.filters.CompositeFilter#createFinalResult
      return new Result(Arrays.asList(
        new CreateExceptionBreakpointResult(start + matcher.start(), start + matcher.end(), exceptionFqn),
        new ResultItem(0, 0, null)));
    }
  }

  private static class CreateExceptionBreakpointResult extends Filter.ResultItem implements InlayProvider {
    private final String myExceptionFqn;

    CreateExceptionBreakpointResult(int highlightStartOffset, int highlightEndOffset, String exceptionFqn) {
      super(highlightStartOffset, highlightEndOffset, null);
      myExceptionFqn = exceptionFqn;
    }

    @Override
    public EditorCustomElementRenderer createInlayRenderer(Editor editor) {
      PresentationFactory factory = new PresentationFactory((EditorImpl)editor);
      InlayPresentation presentation =
        factory.referenceOnHover(factory.roundWithBackground(factory.smallText("Create breakpoint")), (event, point) -> {
          Project project = editor.getProject();
          XBreakpoint<JavaExceptionBreakpointProperties> breakpoint =
            XDebuggerManager.getInstance(project).getBreakpointManager().getBreakpoints(JavaExceptionBreakpointType.class).stream()
              .filter(b -> Objects.equals(myExceptionFqn, b.getProperties().myQualifiedName)).findFirst().orElse(null);
          if (breakpoint == null) {
            ExceptionBreakpoint ebpt = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager()
              .addExceptionBreakpoint(myExceptionFqn, StringUtil.getPackageName(myExceptionFqn));
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
