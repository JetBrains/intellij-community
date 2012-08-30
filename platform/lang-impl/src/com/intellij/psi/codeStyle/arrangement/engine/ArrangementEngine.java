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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.ArrangementRule;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.StdArrangementRule;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

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

    Rearranger<?> rearranger = Rearranger.EXTENSION.forLanguage(file.getLanguage());
    if (rearranger == null) {
      return;
    }

    CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(file.getProject()).getCurrentSettings();
    final Ref<List<? extends ArrangementRule>> rulesRef = new Ref<List<? extends ArrangementRule>>();
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
    else {
      rulesRef.set(arrangementRules);
    }
    
    final Collection<? extends ArrangementEntry> entriesToProcess = rearranger.parse(file, document, ranges);
    final DocumentEx documentEx;
    if (document instanceof DocumentEx && !((DocumentEx)document).isInBulkUpdate()) {
      documentEx = (DocumentEx)document;
    }
    else {
      documentEx = null;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        if (documentEx != null) {
          documentEx.setInBulkUpdate(true);
        }
        try {
          doArrange(document, rulesRef.get(), entriesToProcess);
        }
        finally {
          if (documentEx != null) {
            documentEx.setInBulkUpdate(false);
          }
        }
      }
    });
  }

  private static void doArrange(@NotNull final Document document,
                                @NotNull List<? extends ArrangementRule> arrangementRules,
                                @NotNull Collection<? extends ArrangementEntry> entriesToProcess)
  {
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
    //      list: Entry1 Entry2
    //      stack: [0, 0, 2]
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
    entries.addAll(entriesToProcess);
    stack.push(new StackEntry(0, entriesToProcess.size()));
    while (!stack.isEmpty()) {
      StackEntry stackEntry = stack.peek();
      if (stackEntry.current >= stackEntry.end) {
        List<ArrangementEntry> subEntries = entries.subList(stackEntry.start, stackEntry.end);
        if (subEntries.size() > 1) {
          doArrange(arrangementRules, subEntries, document);
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

  private static void doArrange(@NotNull List<? extends  ArrangementRule> rules,
                                @NotNull List<? extends  ArrangementEntry> entries,
                                @NotNull Document document)
  {
    List<ArrangementEntry> arranged = new ArrayList<ArrangementEntry>();
    Set<ArrangementEntry> unprocessed = new LinkedHashSet<ArrangementEntry>(entries);

    for (ArrangementRule rule : rules) {
      for (ArrangementEntry entry : entries) {
        if (entry.canBeMatched() && unprocessed.contains(entry) && rule.getMatcher().isMatched(entry)) {
          arranged.add(entry);
          unprocessed.remove(entry);
        }
      }
    }
    arranged.addAll(unprocessed);

    if (arranged.equals(entries)) {
      return;
    }
    
    // We apply changes from the last position to the first position in order not to bother with offsets shifts.
    ArrangementEntry parent = entries.get(0).getParent();
    final String initial;
    final int shift;
    if (parent == null) {
      initial = document.getCharsSequence().toString();
      shift = 0;
    }
    else {
      initial = document.getCharsSequence().subSequence(parent.getStartOffset(), parent.getEndOffset()).toString();
      shift = parent.getStartOffset();
    }
    for (int i = arranged.size() - 1; i >= 0; i--) {
      ArrangementEntry arrangedEntry = arranged.get(i);
      ArrangementEntry initialEntry = entries.get(i);
      if (!arrangedEntry.equals(initialEntry)) {
        String text = initial.substring(arrangedEntry.getStartOffset() - shift, arrangedEntry.getEndOffset() - shift);
        document.replaceString(initialEntry.getStartOffset(), initialEntry.getEndOffset(), text);
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
