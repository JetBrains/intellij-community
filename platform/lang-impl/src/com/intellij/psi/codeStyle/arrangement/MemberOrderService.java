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
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.engine.ArrangementEngine;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The whole arrangement idea is to allow to change file entries order according to the user-provided rules.
 * <p/>
 * That means that we can re-use the same mechanism during, say, new members generation - arrangement rules can be used to
 * determine position where a new element should be inserted.
 * <p/>
 * This service provides utility methods for that.
 * 
 * @author Denis Zhdanov
 * @since 9/4/12 11:12 AM
 */
public class MemberOrderService {
  
  /**
   * Tries to find an element at the given context which should be the previous sibling for the given 'member'element according to the
   * {@link CommonCodeStyleSettings#getArrangementRules() user-defined arrangement rules}.
   * <p/>
   * E.g. the IDE might generate given 'member' element and wants to know element after which it should be inserted
   * 
   * @param member    target member which anchor should be calculated
   * @param settings  code style settings to use
   * @param context   given member's context
   * @return          given member's anchor if the one can be computed;
   *                  given 'context' element if given member should be the first child
   *                  <code>null</code> otherwise
   */
  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  public PsiElement getAnchor(@NotNull PsiElement member, @NotNull CommonCodeStyleSettings settings, @NotNull PsiElement context) {
    Language language = context.getLanguage();
    Rearranger<?> rearranger = Rearranger.EXTENSION.forLanguage(language);
    if (rearranger == null) {
      return null;
    }

    List<? extends ArrangementRule> rules = settings.getArrangementRules();
    if (rules.isEmpty() && rearranger instanceof ArrangementStandardSettingsAware) {
      rules = ((ArrangementStandardSettingsAware)rearranger).getDefaultRules();
    }

    if (rules == null) {
      return null;
    }

    ArrangementEntry memberEntry = rearranger.wrap(member);
    if (memberEntry == null) {
      return null;
    }
    
    List<? extends ArrangementEntry> entries = rearranger.parse(context, null, Collections.singleton(context.getTextRange()));
    if (entries.isEmpty()) {
      return null;
    }

    ArrangementEntry parentEntry = entries.get(0);
    List<? extends ArrangementEntry> nonArranged = parentEntry.getChildren();
    List<ArrangementEntry> entriesWithNew = new ArrayList<ArrangementEntry>(nonArranged);
    entriesWithNew.add(memberEntry);
    List<ArrangementEntry> arranged = ArrangementEngine.arrange(entriesWithNew, rules);
    int i = arranged.indexOf(memberEntry);
    
    if (i <= 0) {
      return context;
    }

    ArrangementEntry anchorEntry = null;
    if (i >= arranged.size() - 1) {
      anchorEntry = nonArranged.get(nonArranged.size() - 1);
    }
    else {
      Set<ArrangementEntry> entriesBelow = new HashSet<ArrangementEntry>();
      entriesBelow.addAll(arranged.subList(i + 1, arranged.size()));
      for (ArrangementEntry entry : nonArranged) {
        if (entriesBelow.contains(entry)) {
          break;
        }
        anchorEntry = entry;
      }
    }

    if (anchorEntry == null) {
      return context;
    }
    
    return context.findElementAt(anchorEntry.getEndOffset() - context.getTextRange().getStartOffset());
  }
}
