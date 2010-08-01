/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author mike
 */
public class HippieWordCompletionHandler implements CodeInsightActionHandler {
  private static final Key<CompletionState> KEY_STATE = new Key<CompletionState>("HIPPIE_COMPLETION_STATE");
  private final Direction myDirection;

  public HippieWordCompletionHandler(final Direction direction) {
    myDirection = direction;
  }

  enum Direction {
    FORWARD,
    BACKWARD
  }

  public void invoke(@NotNull Project project, @NotNull final Editor editor, @NotNull PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    final CharSequence charsSequence = editor.getDocument().getCharsSequence();

    final CompletionData data = computeData(editor, charsSequence);
    String currentPrefix = data.myPrefix;

    final CompletionState completionState = getCompletionState(editor);

    String oldPrefix = completionState.oldPrefix;
    CompletionVariant lastProposedVariant = completionState.lastProposedVariant;

    if (lastProposedVariant == null || oldPrefix == null || !new CamelHumpMatcher(oldPrefix).prefixMatches(currentPrefix) || //oldPrefix.length() == 0 ||
        !currentPrefix.equals(lastProposedVariant.variant)) {
      //we are starting over
      oldPrefix = currentPrefix;
      completionState.oldPrefix = oldPrefix;
      lastProposedVariant = null;
    }

    CompletionVariant nextVariant = computeNextVariant(editor, oldPrefix, lastProposedVariant, data);
    if (nextVariant == null) return;

    int replacementEnd = data.startOffset + data.myWordUnderCursor.length();
    editor.getDocument().replaceString(data.startOffset, replacementEnd, nextVariant.variant);
    editor.getCaretModel().moveToOffset(data.startOffset + nextVariant.variant.length());
    completionState.lastProposedVariant = nextVariant;
    highlightWord(editor, nextVariant, project, data);
  }

  private static void highlightWord(final Editor editor, final CompletionVariant variant, final Project project, CompletionData data) {
    int delta = data.startOffset < variant.offset ? variant.variant.length() - data.myWordUnderCursor.length() : 0;

    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager colorManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES);
    highlightManager.addOccurrenceHighlight(editor, variant.offset + delta, variant.offset + variant.variant.length() + delta, attributes,
                                            HighlightManager.HIDE_BY_ANY_KEY, null, null);
  }


  private static class CompletionData {
    public String myPrefix;
    public String myWordUnderCursor;
    public int startOffset;
  }

  @Nullable
  private CompletionVariant computeNextVariant(final Editor editor,
                                               @Nullable final String prefix,
                                               @Nullable CompletionVariant lastProposedVariant,
                                               final CompletionData data) {
    final List<CompletionVariant> variants = computeVariants(editor, prefix);
    if (variants.isEmpty()) return null;

    for (CompletionVariant variant : variants) {
      if (lastProposedVariant != null) {
        if (variant.variant.equals(lastProposedVariant.variant)) {
          if (lastProposedVariant.offset > data.startOffset && variant.offset > data.startOffset) lastProposedVariant = variant;
          if (lastProposedVariant.offset < data.startOffset && variant.offset < data.startOffset) lastProposedVariant = variant;
        }
      }
    }


    if (lastProposedVariant == null) {
      CompletionVariant result = null;

      if (myDirection == Direction.FORWARD) {
        for (CompletionVariant variant : variants) {
          if (variant.offset < data.startOffset) {
            result = variant;
          }
          else if (result == null) {
            result = variant;
            break;
          }
        }
      }
      else {
        for (CompletionVariant variant : variants) {
          if (variant.offset > data.startOffset) return variant;
        }

        return variants.iterator().next();
      }

      return result;
    }


    if (myDirection == Direction.FORWARD) {
      CompletionVariant result = null;
      for (CompletionVariant variant : variants) {
        if (variant == lastProposedVariant) {
          if (result == null) return variants.get(variants.size() - 1);
          return result;
        }
        result = variant;
      }

      return variants.get(variants.size() - 1);
    }
    else {
      for (Iterator<CompletionVariant> i = variants.iterator(); i.hasNext();) {
        CompletionVariant variant = i.next();
        if (variant == lastProposedVariant) {
          if (i.hasNext()) {
            return i.next();
          }
          else {
            return variants.iterator().next();
          }
        }
      }

    }

    return null;
  }

  public static class CompletionVariant {
    public final String variant;
    public final int offset;

    public CompletionVariant(final String variant, final int offset) {
      this.variant = variant;
      this.offset = offset;
    }
  }

  private static List<CompletionVariant> computeVariants(@NotNull final Editor editor, @Nullable final String prefix) {
    final PrefixMatcher matcher = new CamelHumpMatcher(prefix == null ? "" : prefix); 

    final CharSequence chars = editor.getDocument().getCharsSequence();

    final ArrayList<CompletionVariant> words = new ArrayList<CompletionVariant>();
    final List<CompletionVariant> afterWords = new ArrayList<CompletionVariant>();

    IdTableBuilding.scanWords(new IdTableBuilding.ScanWordProcessor() {
      public void run(final CharSequence chars, final int start, final int end) {
        final int caretOffset = editor.getCaretModel().getOffset();
        if (start <= caretOffset && end >= caretOffset) return; //skip prefix itself

        final String word = chars.subSequence(start, end).toString();
        if (!matcher.prefixMatches(word)) return;
        final CompletionVariant v = new CompletionVariant(word, start);

        if (end > caretOffset) {
          afterWords.add(v);
        }
        else {
          words.add(v);
        }
      }
    }, chars, 0, chars.length());


    Set<String> allWords = new HashSet<String>();
    List<CompletionVariant> result = new ArrayList<CompletionVariant>();

    Collections.reverse(words);

    for (CompletionVariant variant : words) {
      if (!allWords.contains(variant.variant)) {
        result.add(variant);
        allWords.add(variant.variant);
      }
    }

    Collections.reverse(result);

    allWords.clear();
    for (CompletionVariant variant : afterWords) {
      if (!allWords.contains(variant.variant)) {
        result.add(variant);
        allWords.add(variant.variant);
      }
    }

    return result;
  }

  private static CompletionData computeData(final Editor editor, final CharSequence charsSequence) {
    int offset = editor.getCaretModel().getOffset();

    while (offset > 1 && Character.isJavaIdentifierPart(charsSequence.charAt(offset - 1))) offset--;

    final CompletionData data = new CompletionData();

    int endOffset = offset;
    data.startOffset = offset;
    while (charsSequence.length() > endOffset && Character.isJavaIdentifierPart(charsSequence.charAt(endOffset)) &&
           endOffset < editor.getDocument().getTextLength()) {
      if (endOffset == editor.getCaretModel().getOffset()) {
        data.myPrefix = charsSequence.subSequence(offset, endOffset).toString();
      }
      endOffset++;
    }

    data.myWordUnderCursor = charsSequence.subSequence(offset, endOffset).toString();
    if (data.myPrefix == null) data.myPrefix = data.myWordUnderCursor;

    return data;
  }

  public boolean startInWriteAction() {
    return true;
  }

  private static CompletionState getCompletionState(Editor editor) {
    CompletionState state = editor.getUserData(KEY_STATE);
    if (state == null) {
      state = new CompletionState();
      editor.putUserData(KEY_STATE, state);
    }

    return state;
  }

  private static class CompletionState {
    public String oldPrefix;
    public CompletionVariant lastProposedVariant;
  }
}
