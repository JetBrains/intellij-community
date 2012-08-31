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
import gnu.trove.TObjectIntHashMap;
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

    List<ArrangementEntry> entries = new ArrayList<ArrangementEntry>();
    Stack<StackEntry> stack = new Stack<StackEntry>();
    entries.addAll(context.entries);
    stack.push(new StackEntry(0, context.entries.size()));
    while (!stack.isEmpty()) {
      StackEntry stackEntry = stack.peek();
      if (stackEntry.current >= stackEntry.end) {
        List<E> subEntries = (List<E>)entries.subList(stackEntry.start, stackEntry.end);
        if (subEntries.size() > 1) {
          doArrange(subEntries, context);
        }
        subEntries.clear();
        stack.pop();
      }
      else {
        ArrangementEntry entry = entries.get(stackEntry.current++);
        Collection<? extends ArrangementEntry> children = entry.getChildren();
        if (!children.isEmpty()) {
          entries.addAll(children);
          stack.push(new StackEntry(stackEntry.end, children.size()));
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static <E extends ArrangementEntry> void doArrange(@NotNull List<E> entries,
                                                             @NotNull Context<E> context)
  {
    List<E> arranged = new ArrayList<E>();
    Set<E> unprocessed = new LinkedHashSet<E>(entries);

    for (ArrangementRule rule : context.rules) {
      for (E entry : entries) {
        if (entry.canBeMatched() && unprocessed.contains(entry) && rule.getMatcher().isMatched(entry)) {
          arranged.add(entry);
          unprocessed.remove(entry);
        }
      }
    }
    arranged.addAll(unprocessed);
    
    context.prepare(arranged);
    // We apply changes from the last position to the first position in order not to bother with offsets shifts.
    for (int i = arranged.size() - 1; i >= 0; i--) {
      E arrangedEntry = arranged.get(i);
      E initialEntry = entries.get(i);
      context.replace(initialEntry, arrangedEntry, (E)arrangedEntry.getParent(), i > 0 ? arranged.get(i - 1) : null);
    }
  }

  private static class Context<E extends ArrangementEntry> {

    @NotNull public final Rearranger<E>                   rearranger;
    @NotNull public final Collection<E>                   entries;
    @NotNull public final Document                        document;
    @NotNull public final List<? extends ArrangementRule> rules;
    @NotNull public final CodeStyleSettings               mySettings;

    /** Holds information on how many symbols was added to the initial entry text during the processing. */
    @NotNull private final TObjectIntHashMap<ArrangementEntry> myExtraSizes = new TObjectIntHashMap<ArrangementEntry>();
    @NotNull private String myParentText;
    private          int    myParentShift;

    private Context(@NotNull Rearranger<E> rearranger,
                    @NotNull Collection<E> entries,
                    @NotNull Document document,
                    @NotNull List<? extends ArrangementRule> rules,
                    @NotNull CodeStyleSettings settings)
    {
      this.rearranger = rearranger;
      this.entries = entries;
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
      return new Context<T>(rearranger, entries, document, rules, settings);
    }

    public void prepare(@NotNull List<E> arrangedEntries) {
      if (arrangedEntries.isEmpty()) {
        return;
      }
      E parent = arrangedEntries.get(0);
      if (parent == null) {
        myParentText = document.getText();
        myParentShift = 0;
      }
      else {
        int endOffset = parent.getEndOffset();
        if (myExtraSizes.containsKey(parent)) {
          endOffset += myExtraSizes.get(parent);
        }
        myParentText = document.getCharsSequence().subSequence(parent.getStartOffset(), endOffset).toString();
        myParentShift = parent.getStartOffset();
      }
    }

    /**
     * Replaces given 'old entry' by the given 'new entry'.
     *
     * @param oldEntry  entry which range should be replaced by the given 'new entry'
     * @param newEntry  entry which text should replace given 'old entry' range
     * @param parent    parent entry for the given entries
     * @param previous  previous entry for the 'new entry' (if any)
     */
    @SuppressWarnings("AssignmentToForLoopParameter")
    public void replace(@NotNull E oldEntry, @NotNull E newEntry, @Nullable E parent, @Nullable E previous) {
      // Calculate blank lines before the arrangement.
      int blankLinesBefore = 0;
      TIntArrayList lineFeedOffsets = new TIntArrayList();
      int oldStartLine = document.getLineNumber(oldEntry.getStartOffset());
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

      int desiredBlankLinesNumber = rearranger.getBlankLines(mySettings, parent, previous, newEntry);
      if (desiredBlankLinesNumber == blankLinesBefore && newEntry.equals(oldEntry)) {
        return;
      }

      String newEntryText = myParentText.substring(newEntry.getStartOffset() - myParentShift, newEntry.getEndOffset() - myParentShift);
      int lineFeedsDiff = desiredBlankLinesNumber - blankLinesBefore;
      if (lineFeedsDiff == 0) {
        document.replaceString(oldEntry.getStartOffset(), oldEntry.getEndOffset(), newEntryText);
        return;
      }
      
      int oldEndOffset = oldEntry.getEndOffset();
      if (myExtraSizes.containsKey(oldEntry)) {
        oldEndOffset += myExtraSizes.get(oldEntry);
      }
      if (lineFeedsDiff > 0) {
        StringBuilder buffer = new StringBuilder(StringUtil.repeat("\n", lineFeedsDiff));
        buffer.append(newEntryText);
        document.replaceString(oldEntry.getStartOffset(), oldEndOffset, buffer);
        for (ArrangementEntry entry = newEntry; entry != null; entry = entry.getParent()) {
          if (myExtraSizes.containsKey(entry)) {
            myExtraSizes.put(entry, myExtraSizes.get(entry) + lineFeedsDiff);
          }
          else {
            myExtraSizes.put(entry, lineFeedsDiff);
          }
        }
      }
      else if (desiredBlankLinesNumber == blankLinesBefore) {
        document.replaceString(oldEntry.getStartOffset(), oldEndOffset, newEntryText);
      }
      else {
        // Cut exceeding blank lines.
        int blankLinesToCut = blankLinesBefore - desiredBlankLinesNumber;
        int replacementStartOffset = lineFeedOffsets.get(lineFeedOffsets.size() - blankLinesToCut);
        document.replaceString(replacementStartOffset, oldEndOffset, newEntryText);
      }
      for (ArrangementEntry entry = newEntry; entry != null; entry = entry.getParent()) {
        if (myExtraSizes.containsKey(entry)) {
          myExtraSizes.put(entry, myExtraSizes.get(entry) + lineFeedsDiff);
        }
        else {
          myExtraSizes.put(entry, lineFeedsDiff);
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
