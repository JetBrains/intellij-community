/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement.engine;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.ArrangementRule;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.StdArrangementRule;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.util.containers.Stack;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 7/20/12 1:56 PM
 */
public class ArrangementEngine {

  /**
   * Arranges given PSI root contents that belong to the given ranges.
   * 
   * @param file    target PSI root
   * @param ranges  target ranges to use within the given root
   */
  @SuppressWarnings("MethodMayBeStatic")
  public void arrange(@NotNull PsiFile file, @NotNull Collection<TextRange> ranges) {
    final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) {
      return;
    }

    final Rearranger<?> rearranger = Rearranger.EXTENSION.forLanguage(file.getLanguage());
    if (rearranger == null) {
      return;
    }

    final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(file.getProject()).getCurrentSettings();
    List<? extends ArrangementRule> arrangementRules = settings.getCommonSettings(file.getLanguage()).getArrangementRules();
    if (arrangementRules.isEmpty() && rearranger instanceof ArrangementStandardSettingsAware) {
      List<StdArrangementRule> defaultRules = ((ArrangementStandardSettingsAware)rearranger).getDefaultRules();
      if (defaultRules != null) {
        arrangementRules = defaultRules;
      }
    }
    if (arrangementRules.isEmpty()) {
      return;
    }
    
    final DocumentEx documentEx;
    if (document instanceof DocumentEx && !((DocumentEx)document).isInBulkUpdate()) {
      documentEx = (DocumentEx)document;
    }
    else {
      documentEx = null;
    }

    final Context<? extends ArrangementEntry> context = Context.from(rearranger, document, file, ranges, arrangementRules, settings);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        if (documentEx != null) {
          documentEx.setInBulkUpdate(true);
        }
        try {
          doArrange(context);
        }
        finally {
          if (documentEx != null) {
            documentEx.setInBulkUpdate(false);
          }
        }
      }
    });
  }

  @SuppressWarnings("unchecked")
  private static <E extends ArrangementEntry> void doArrange(Context<E> context) {
    // The general idea is to process entries bottom-up where every processed group belongs to the same parent. We may not bother
    // with entries text ranges then. We use a list and a stack for achieving that than.
    //
    // Example:
    //            Entry1              Entry2
    //            /    \              /    \
    //      Entry11   Entry12    Entry21  Entry22
    //
    //    --------------------------
    //    Stage 1:
    //      list: Entry1 Entry2    <-- entries to process
    //      stack: [0, 0, 2]       <-- holds current iteration info at the following format:
    //                                 (start entry index at the auxiliary list (inclusive); current index; end index (exclusive))
    //    --------------------------
    //    Stage 2:
    //      list: Entry1 Entry2 Entry11 Entry12
    //      stack: [0, 1, 2]
    //             [2, 2, 4]
    //    --------------------------
    //    Stage 3:
    //      list: Entry1 Entry2 Entry11 Entry12
    //      stack: [0, 1, 2]
    //             [2, 3, 4]
    //    --------------------------
    //    Stage 4:
    //      list: Entry1 Entry2 Entry11 Entry12
    //      stack: [0, 1, 2]
    //             [2, 4, 4]
    //    --------------------------
    //      arrange 'Entry11 Entry12'
    //    --------------------------
    //    Stage 5:
    //      list: Entry1 Entry2 
    //      stack: [0, 1, 2]
    //    --------------------------
    //    Stage 6:
    //      list: Entry1 Entry2 Entry21 Entry22
    //      stack: [0, 2, 2]
    //             [2, 2, 4]
    //    --------------------------
    //    Stage 7:
    //      list: Entry1 Entry2 Entry21 Entry22
    //      stack: [0, 2, 2]
    //             [2, 3, 4]
    //    --------------------------
    //    Stage 8:
    //      list: Entry1 Entry2 Entry21 Entry22
    //      stack: [0, 2, 2]
    //             [2, 4, 4]
    //    --------------------------
    //      arrange 'Entry21 Entry22'
    //    --------------------------
    //    Stage 9:
    //      list: Entry1 Entry2
    //      stack: [0, 2, 2]
    //    --------------------------
    //      arrange 'Entry1 Entry2'

    List<ArrangementEntryWrapper<E>> entries = new ArrayList<ArrangementEntryWrapper<E>>();
    Stack<StackEntry> stack = new Stack<StackEntry>();
    entries.addAll(context.wrappers);
    stack.push(new StackEntry(0, context.wrappers.size()));
    while (!stack.isEmpty()) {
      StackEntry stackEntry = stack.peek();
      if (stackEntry.current >= stackEntry.end) {
        List<ArrangementEntryWrapper<E>> subEntries = entries.subList(stackEntry.start, stackEntry.end);
        if (subEntries.size() > 1) {
          doArrange(subEntries, context);
        }
        subEntries.clear();
        stack.pop();
      }
      else {
        ArrangementEntryWrapper<E> wrapper = entries.get(stackEntry.current++);
        List<ArrangementEntryWrapper<E>> children = wrapper.getChildren();
        if (!children.isEmpty()) {
          entries.addAll(children);
          stack.push(new StackEntry(stackEntry.end, children.size()));
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static <E extends ArrangementEntry> void doArrange(@NotNull List<ArrangementEntryWrapper<E>> entries,
                                                             @NotNull Context<E> context)
  {
    List<ArrangementEntryWrapper<E>> arranged = new ArrayList<ArrangementEntryWrapper<E>>();
    Set<ArrangementEntryWrapper<E>> unprocessed = new LinkedHashSet<ArrangementEntryWrapper<E>>(entries);

    for (ArrangementRule rule : context.rules) {
      for (ArrangementEntryWrapper<E> wrapper : entries) {
        if (wrapper.getEntry().canBeMatched() && unprocessed.contains(wrapper) && rule.getMatcher().isMatched(wrapper.getEntry())) {
          arranged.add(wrapper);
          unprocessed.remove(wrapper);
        }
      }
    }
    arranged.addAll(unprocessed);
    
    context.prepare(arranged);
    // We apply changes from the last position to the first position in order not to bother with offsets shifts.
    for (int i = arranged.size() - 1; i >= 0; i--) {
      ArrangementEntryWrapper<E> arrangedWrapper = arranged.get(i);
      ArrangementEntryWrapper<E> initialWrapper = entries.get(i);
      context.replace(arrangedWrapper, initialWrapper, i > 0 ? arranged.get(i - 1) : null);
    }
  }

  private static class Context<E extends ArrangementEntry> {

    @NotNull public final Rearranger<E>                          rearranger;
    @NotNull public final Collection<ArrangementEntryWrapper<E>> wrappers;
    @NotNull public final Document                               document;
    @NotNull public final List<? extends ArrangementRule>        rules;
    @NotNull public final CodeStyleSettings                      mySettings;

    @NotNull private String myParentText;
    private          int    myParentShift;

    private Context(@NotNull Rearranger<E> rearranger,
                    @NotNull Collection<ArrangementEntryWrapper<E>> wrappers,
                    @NotNull Document document,
                    @NotNull List<? extends ArrangementRule> rules,
                    @NotNull CodeStyleSettings settings)
    {
      this.rearranger = rearranger;
      this.wrappers = wrappers;
      this.document = document;
      this.rules = rules;
      mySettings = settings;
    }

    public static <T extends ArrangementEntry> Context<T> from(@NotNull Rearranger<T> rearranger,
                                                               @NotNull Document document,
                                                               @NotNull PsiElement root,
                                                               @NotNull Collection<TextRange> ranges,
                                                               @NotNull List<? extends ArrangementRule> rules,
                                                               @NotNull CodeStyleSettings settings)
    {
      Collection<T> entries = rearranger.parse(root, document, ranges);
      Collection<ArrangementEntryWrapper<T>> wrappers = new ArrayList<ArrangementEntryWrapper<T>>();
      ArrangementEntryWrapper<T> previous = null;
      for (T entry : entries) {
        ArrangementEntryWrapper<T> wrapper = new ArrangementEntryWrapper<T>(entry);
        if (previous != null) {
          previous.setNext(wrapper);
          wrapper.setPrevious(previous);
        }
        wrappers.add(wrapper);
        previous = wrapper;
      }
      return new Context<T>(rearranger, wrappers, document, rules, settings);
    }

    public void prepare(@NotNull List<ArrangementEntryWrapper<E>> arrangedEntries) {
      if (arrangedEntries.isEmpty()) {
        return;
      }
      ArrangementEntryWrapper<E> parent = arrangedEntries.get(0).getParent();
      if (parent == null) {
        myParentText = document.getText();
        myParentShift = 0;
      }
      else {
        myParentText = document.getCharsSequence().subSequence(parent.getStartOffset(), parent.getEndOffset()).toString();
        myParentShift = parent.getStartOffset();
      }
    }

    /**
     * Replaces given 'old entry' by the given 'new entry'.
     *
     * @param newWrapper  wrapper for an entry which text should replace given 'old entry' range
     * @param oldWrapper  wrapper for an entry which range should be replaced by the given 'new entry'
     * @param previous    wrapper which will be previous for the entry referenced via the given 'new wrapper'
     */
    @SuppressWarnings("AssignmentToForLoopParameter")
    public void replace(@NotNull ArrangementEntryWrapper<E> newWrapper,
                        @NotNull ArrangementEntryWrapper<E> oldWrapper,
                        @Nullable ArrangementEntryWrapper<E> previous)
    {
      // Calculate blank lines before the arrangement.
      int blankLinesBefore = 0;
      TIntArrayList lineFeedOffsets = new TIntArrayList();
      int oldStartLine = document.getLineNumber(oldWrapper.getStartOffset());
      if (oldStartLine > 0) {
        int lastLineFeed = document.getLineStartOffset(oldStartLine) - 1;
        lineFeedOffsets.add(lastLineFeed);
        for (int i = lastLineFeed - 1 - myParentShift; i >= 0; i--) {
          i = CharArrayUtil.shiftBackward(myParentText, i, " \t");
          if (myParentText.charAt(i) == '\n') {
            blankLinesBefore++;
            lineFeedOffsets.add(i + myParentShift);
          }
          else {
            break;
          }
        }
      }

      ArrangementEntryWrapper<E> parentWrapper = oldWrapper.getParent();
      int desiredBlankLinesNumber = rearranger.getBlankLines(mySettings,
                                                             parentWrapper == null ? null : parentWrapper.getEntry(),
                                                             previous == null ? null : previous.getEntry(),
                                                             newWrapper.getEntry());
      if (desiredBlankLinesNumber == blankLinesBefore && newWrapper.equals(oldWrapper)) {
        return;
      }

      String newEntryText = myParentText.substring(newWrapper.getStartOffset() - myParentShift, newWrapper.getEndOffset() - myParentShift);
      int lineFeedsDiff = desiredBlankLinesNumber - blankLinesBefore;
      if (lineFeedsDiff == 0 || desiredBlankLinesNumber < 0) {
        document.replaceString(oldWrapper.getStartOffset(), oldWrapper.getEndOffset(), newEntryText);
        return;
      }
      
      if (lineFeedsDiff > 0) {
        // Insert necessary number of blank lines.
        StringBuilder buffer = new StringBuilder(StringUtil.repeat("\n", lineFeedsDiff));
        buffer.append(newEntryText);
        document.replaceString(oldWrapper.getStartOffset(), oldWrapper.getEndOffset(), buffer);
      }
      else {
        // Cut exceeding blank lines.
        int replacementStartOffset = lineFeedOffsets.get(lineFeedOffsets.size() + lineFeedsDiff);
        document.replaceString(replacementStartOffset, oldWrapper.getEndOffset(), newEntryText);
      }
      
      // Update wrapper ranges.
      ArrangementEntryWrapper<E> parent = oldWrapper.getParent();
      if (parent == null) {
        return;
      }

      Deque<ArrangementEntryWrapper<E>> parents = new ArrayDeque<ArrangementEntryWrapper<E>>();
      do {
        parents.add(parent);
        parent.setEndOffset(parent.getEndOffset() + lineFeedsDiff);
        parent = parent.getParent();
      } while (parent != null);


      while (!parents.isEmpty()) {

        for (ArrangementEntryWrapper<E> wrapper = parents.removeLast().getNext(); wrapper != null; wrapper = wrapper.getNext()) {
          wrapper.applyShift(lineFeedsDiff);
        }
      }
    }
  }

  private static class StackEntry {

    public int start;
    public int current;
    public int end;

    StackEntry(int start, int count) {
      this.start = start;
      current = start;
      end = start + count;
    }
  }
}
