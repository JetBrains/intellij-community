// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeEditor.printing;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.HighlightingSessionImpl;
import com.intellij.codeInsight.daemon.impl.LineMarkersPass;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.MathUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class FileSeparatorProvider {
  static @NotNull List<LineMarkerInfo<?>> getFileSeparators(@NotNull PsiFile file, @NotNull Document document) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    List<LineMarkerInfo<?>> result = new ArrayList<>();
    // need to run highlighting under DaemonProgressIndicator
    DaemonProgressIndicator indicator = new DaemonProgressIndicator();

    ProgressManager.getInstance().executeProcessUnderProgress(() -> {
      HighlightingSessionImpl.runInsideHighlightingSession(file, null, ProperTextRange.create(file.getTextRange()), false, __ -> {
        for (LineMarkerInfo<?> lineMarkerInfo : LineMarkersPass.queryLineMarkers(file, document)) {
          if (lineMarkerInfo.separatorColor != null) {
            result.add(lineMarkerInfo);
          }
        }
        result.sort(Comparator.comparingInt(i -> getDisplayLine(i, document)));
      });
    }, indicator);
    return result;
  }

  static int getDisplayLine(@NotNull LineMarkerInfo<?> lineMarkerInfo, @NotNull Document document) {
    int offset = lineMarkerInfo.separatorPlacement == SeparatorPlacement.TOP ? lineMarkerInfo.startOffset : lineMarkerInfo.endOffset;
    return document.getLineNumber(MathUtil.clamp(offset, 0, document.getTextLength())) +
           (lineMarkerInfo.separatorPlacement == SeparatorPlacement.TOP ? 0 : 1);
  }
}