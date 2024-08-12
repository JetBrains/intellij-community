// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.internal;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.Map;

public final class DumpLookupElementWeights extends AnAction implements DumbAware {

  private static final Logger LOG = Logger.getInstance(DumpLookupElementWeights.class);

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    dumpLookupElementWeights((LookupImpl)LookupManager.getActiveLookup(editor));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    presentation.setEnabled(editor != null && LookupManager.getActiveLookup(editor) != null);
  }

  public static void dumpLookupElementWeights(final LookupImpl lookup) {
    LookupElement selected = lookup.getCurrentItem();
    String sb = "selected: " + selected;
    if (selected != null) {
      sb += "\nprefix: " + lookup.itemPattern(selected);
    }
    sb += "\nweights:\n" + StringUtil.join(getLookupElementWeights(lookup, true), "\n");
    System.out.println(sb);
    LOG.info(sb);
    try {
      CopyPasteManager.getInstance().setContents(new StringSelection(sb));
    } catch (Exception ignore){}
  }

  public static List<String> getLookupElementWeights(LookupImpl lookup, boolean hideSingleValued) {
    final Map<LookupElement, List<Pair<String, Object>>> weights = lookup.getRelevanceObjects(lookup.getItems(), hideSingleValued);
    return ContainerUtil.map(weights.entrySet(), entry -> entry.getKey().getLookupString() + "\t" + StringUtil.join(entry.getValue(), pair -> pair.first + "=" + pair.second, ", "));
  }
}