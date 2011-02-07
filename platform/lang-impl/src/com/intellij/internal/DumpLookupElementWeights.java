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

import com.intellij.codeInsight.completion.CompletionLookupArranger;
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
import com.intellij.openapi.util.text.StringUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    final List<LookupElement> items = lookup.getItems();
    final int count = lookup.getPreferredItemsCount();
    final Pattern pattern = Pattern.compile("[\\[ ](([a-zA-Z0-9.@])+)=(([a-zA-Z0-9.@])+)[,\\]]");
    Map<String, String> values = new HashMap<String, String>();
    Set<String> toExclude = new HashSet<String>();
    for (LookupElement item : items) {
      final String weight = item.getUserData(CompletionLookupArranger.RELEVANCE_KEY).toString();
      final Matcher matcher = pattern.matcher(weight);
      int start = 0;
      while (matcher.find(start)) {
        start = matcher.end();
        final String name = matcher.group(1);
        final String value = matcher.group(3);
        values.put(name, values.containsKey(name) && !value.equals(values.get(name)) ? null : value);
      }
    }

    for (String name : values.keySet()) {
      final String value = values.get(name);
      if (value != null) {
        toExclude.add(name + "=" + value);
      }
    }

    for (int i = 0; i < items.size(); i++) {
      LookupElement item = items.get(i);
      String weight = item.getUserData(CompletionLookupArranger.RELEVANCE_KEY).toString();
      for (String s : toExclude) {
        weight = StringUtil.replace(weight, s, "", false);
      }
      while (weight.contains(", ,")) {
        weight = weight.replaceAll(", ,", ",");
      }
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