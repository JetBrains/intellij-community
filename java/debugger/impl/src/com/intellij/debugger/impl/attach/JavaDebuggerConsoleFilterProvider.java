// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.attach;

import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.presentation.PresentationRenderer;
import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.impl.InlayProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaDebuggerConsoleFilterProvider implements ConsoleFilterProvider {
  @Override
  public Filter @NotNull [] getDefaultFilters(@NotNull Project project) {
    return new Filter[]{new JavaDebuggerAttachFilter()};
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
                                                                    .attach(myTransport, myAddress, null, editor.getProject());
                                                                });
      return new PresentationRenderer(presentation);
    }
  }
}
