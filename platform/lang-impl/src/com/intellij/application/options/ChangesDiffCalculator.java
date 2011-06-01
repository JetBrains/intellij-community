/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.application.options;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.actions.MergeOperations;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.fragments.Fragment;
import com.intellij.openapi.diff.impl.fragments.FragmentHighlighterImpl;
import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.openapi.diff.impl.highlighting.DiffMarkup;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.processing.TextCompareProcessor;
import com.intellij.openapi.diff.impl.util.TextDiffTypeEnum;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Allows to calculate difference between two versions of document (before and after code style setting value change).
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since 10/14/10 2:44 PM
 */
public class ChangesDiffCalculator {

  private final BaseMarkup myOldMarkup = new BaseMarkup(FragmentSide.SIDE1);
  private final ChangesCollector myNewMarkup = new ChangesCollector();
  private final TextCompareProcessor myCompareProcessor = new TextCompareProcessor(ComparisonPolicy.DEFAULT);
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.ChangesDiffCalculator");

  public Collection<TextRange> calculateDiff(@NotNull final Document beforeDocument, @NotNull final Document currentDocument) {
    myNewMarkup.ranges.clear();
    myOldMarkup.document = beforeDocument;
    myNewMarkup.document = currentDocument;

    List<LineFragment> lineFragments = null;
    try {
      lineFragments = myCompareProcessor.process(beforeDocument.getText(), currentDocument.getText());
    }
    catch (FilesTooBigForDiffException e) {
      LOG.info(e);
      return Collections.emptyList();
    }

    for (Iterator<LineFragment> iterator = lineFragments.iterator(); iterator.hasNext();) {
      LineFragment fragment = iterator.next();
      final FragmentHighlighterImpl fragmentHighlighter = new FragmentHighlighterImpl(myOldMarkup, myNewMarkup, !iterator.hasNext());
      fragment.highlight(fragmentHighlighter);
    }

    return new ArrayList<TextRange>(myNewMarkup.ranges);
  }

  /**
   * Base {@link DiffMarkup} implementation that does nothing.
   */
  @SuppressWarnings({"ConstantConditions"})
  private static class BaseMarkup extends DiffMarkup {

    public Document document;
    private final FragmentSide mySide;

    BaseMarkup(FragmentSide side) {
      super(null);
      mySide = side;
    }

    @Override
    public FragmentSide getSide() {
      return mySide;
    }

    @Override
    public DiffContent getContent() {
      return null;
    }

    @Override
    public EditorEx getEditor() {
      return null;
    }

    @Override
    public Document getDocument() {
      return document;
    }

    @Override
    public void addLineMarker(int line, TextAttributesKey type) {
    }

    @Override
    public void addAction(MergeOperations.Operation operation, int lineStartOffset) {
    }

    @Override
    public void highlightText(Fragment fragment, boolean drawBorder) {
    }


    @Override
    public FileEditor getFileEditor() {
      return null;
    }
  }

  private static class ChangesCollector extends BaseMarkup {

    private static final Set<TextDiffTypeEnum> INTERESTED_DIFF_TYPES
      = EnumSet.of(TextDiffTypeEnum.INSERT, TextDiffTypeEnum.DELETED, TextDiffTypeEnum.CHANGED);

    public final List<TextRange> ranges = new ArrayList<TextRange>();

    ChangesCollector() {
      super(FragmentSide.SIDE2);
    }

    @Override
    public void highlightText(Fragment fragment, boolean drawBorder) {
      TextRange currentRange = fragment.getRange(FragmentSide.SIDE2);
      if (INTERESTED_DIFF_TYPES.contains(fragment.getType())) {
        ranges.add(currentRange);
      }
    }
  }
}