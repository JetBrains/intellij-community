// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.StringSelection;

import static com.intellij.diff.util.DiffUtil.getLineCount;

public class LineStatusMarkerPopupActions {
  public static void showDiff(@NotNull LineStatusTrackerI<?> tracker, @NotNull Range range) {
    Project project = tracker.getProject();
    Range ourRange = expand(range, tracker.getDocument(), tracker.getVcsDocument());

    DiffContent vcsContent = createDiffContent(project,
                                               tracker.getVcsDocument(),
                                               tracker.getVirtualFile(),
                                               getVcsTextRange(tracker, ourRange));
    DiffContent currentContent = createDiffContent(project,
                                                   tracker.getDocument(),
                                                   tracker.getVirtualFile(),
                                                   getCurrentTextRange(tracker, ourRange));

    SimpleDiffRequest request = new SimpleDiffRequest(DiffBundle.message("dialog.title.diff.for.range"),
                                                      vcsContent, currentContent,
                                                      DiffBundle.message("diff.content.title.up.to.date"),
                                                      DiffBundle.message("diff.content.title.current.range"));

    DiffManager.getInstance().showDiff(project, request);
  }

  @NotNull
  private static DiffContent createDiffContent(@Nullable Project project,
                                               @NotNull Document document,
                                               @Nullable VirtualFile highlightFile,
                                               @NotNull TextRange textRange) {
    DocumentContent content = DiffContentFactory.getInstance().create(project, document, highlightFile);
    return DiffContentFactory.getInstance().createFragment(project, content, textRange);
  }

  @NotNull
  private static Range expand(@NotNull Range range, @NotNull Document document, @NotNull Document uDocument) {
    boolean canExpandBefore = range.getLine1() != 0 && range.getVcsLine1() != 0;
    boolean canExpandAfter = range.getLine2() < getLineCount(document) && range.getVcsLine2() < getLineCount(uDocument);
    int offset1 = range.getLine1() - (canExpandBefore ? 1 : 0);
    int uOffset1 = range.getVcsLine1() - (canExpandBefore ? 1 : 0);
    int offset2 = range.getLine2() + (canExpandAfter ? 1 : 0);
    int uOffset2 = range.getVcsLine2() + (canExpandAfter ? 1 : 0);
    return new Range(offset1, offset2, uOffset1, uOffset2);
  }

  public static void copyVcsContent(@NotNull LineStatusTrackerI<?> tracker, @NotNull Range range) {
    final String content = getVcsContent(tracker, range) + "\n";
    CopyPasteManager.getInstance().setContents(new StringSelection(content));
  }

  @NotNull
  public static CharSequence getCurrentContent(@NotNull LineStatusTrackerI<?> tracker, @NotNull Range range) {
    return DiffUtil.getLinesContent(tracker.getDocument(), range.getLine1(), range.getLine2());
  }

  @NotNull
  public static CharSequence getVcsContent(@NotNull LineStatusTrackerI<?> tracker, @NotNull Range range) {
    return DiffUtil.getLinesContent(tracker.getVcsDocument(), range.getVcsLine1(), range.getVcsLine2());
  }

  @NotNull
  public static TextRange getCurrentTextRange(@NotNull LineStatusTrackerI<?> tracker, @NotNull Range range) {
    return DiffUtil.getLinesRange(tracker.getDocument(), range.getLine1(), range.getLine2());
  }

  @NotNull
  public static TextRange getVcsTextRange(@NotNull LineStatusTrackerI<?> tracker, @NotNull Range range) {
    return DiffUtil.getLinesRange(tracker.getVcsDocument(), range.getVcsLine1(), range.getVcsLine2());
  }
}
