// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.tools.util.FoldingModelSupport;
import com.intellij.diff.util.LineRange;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

@ApiStatus.Internal
public final class UnifiedFoldingModel extends FoldingModelSupport {
  private @NotNull DisposableLineNumberConvertor myLineNumberConvertor = new DisposableLineNumberConvertor(null);

  public UnifiedFoldingModel(@Nullable Project project, @NotNull EditorEx editor, @NotNull Disposable disposable) {
    super(project, new EditorEx[]{editor}, disposable);
  }

  public @Nullable Data createState(@Nullable List<? extends LineRange> changedLines,
                                    @NotNull Settings settings,
                                    @NotNull Document document,
                                    @NotNull LineNumberConvertor lineConvertor,
                                    int lineCount,
                                    boolean materialiseEmptyRegions) {
    Iterator<int[]> it = map(changedLines, line -> new int[]{
      line.start,
      line.end
    });

    if (it == null || settings.range == -1) return null;

    myLineNumberConvertor = new DisposableLineNumberConvertor(lineConvertor);
    MyFoldingBuilder builder = new MyFoldingBuilder(document, myLineNumberConvertor, lineCount, settings, materialiseEmptyRegions);
    return builder.build(it);
  }

  public @Nullable Data createState(@Nullable List<? extends LineRange> changedLines,
                                    @NotNull Settings settings,
                                    @NotNull Document document,
                                    @NotNull LineNumberConvertor lineConvertor,
                                    int lineCount) {
    return createState(changedLines, settings, document, lineConvertor, lineCount, false);
  }

  public void disposeLineConvertor() {
    myLineNumberConvertor.dispose();
  }

  private static final class MyFoldingBuilder extends FoldingBuilderBase {
    private final @NotNull Document myDocument;
    private final @NotNull DisposableLineNumberConvertor myLineConvertor;

    private MyFoldingBuilder(@NotNull Document document,
                             @NotNull DisposableLineNumberConvertor lineConvertor,
                             int lineCount,
                             @NotNull Settings settings,
                             boolean materialiseEmptyRegions) {
      super(new int[]{lineCount}, settings, materialiseEmptyRegions);
      myDocument = document;
      myLineConvertor = lineConvertor;
    }

    @Override
    protected @Nullable FoldedRangeDescription getDescription(@NotNull Project project, int lineNumber, int index) {
      int masterLine = myLineConvertor.convert(lineNumber);
      if (masterLine == -1) return null;
      return getLineSeparatorDescription(project, myDocument, masterLine);
    }
  }

  private static final class DisposableLineNumberConvertor {
    private volatile @Nullable LineNumberConvertor myConvertor;

    private DisposableLineNumberConvertor(@Nullable LineNumberConvertor convertor) {
      myConvertor = convertor;
    }

    public int convert(int lineNumber) {
      LineNumberConvertor convertor = myConvertor;
      return convertor != null ? convertor.convert(lineNumber) : -1;
    }

    public void dispose() {
      myConvertor = null;
    }
  }
}