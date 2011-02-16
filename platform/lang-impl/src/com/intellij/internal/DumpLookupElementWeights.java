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

package com.intellij.internal;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author peter
 */
public class DumpLookupElementWeights extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.internal.DumpLookupElementWeights");

  public void actionPerformed(final AnActionEvent e) {
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    dumpLookupElementWeights((LookupImpl)LookupManager.getActiveLookup(editor));
  }

  @Override
  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    presentation.setEnabled(editor != null && LookupManager.getActiveLookup(editor) != null);
  }

  public static void dumpLookupElementWeights(final LookupImpl lookup) {
    final LinkedHashMap<LookupElement,StringBuilder> strings = lookup.getRelevanceStrings();

    final List<LookupElement> items = lookup.getItems();
    final int count = lookup.getPreferredItemsCount();

    for (int i = 0; i < items.size(); i++) {
      LookupElement item = items.get(i);
      String weight = strings.get(item).toString();
      final String s = item.getLookupString() + (lookup.isFrozen(item) ? "\t_first_\t" : "\t") + weight;
      System.out.println(s);
      LOG.info(s);
      if (i == count - 1) {
        final String separator = "------------";
        System.out.println(separator);
        LOG.info(separator);
      }
    }
  }

}