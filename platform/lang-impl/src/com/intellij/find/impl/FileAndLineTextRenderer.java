// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class FileAndLineTextRenderer extends ColoredListCellRenderer<UsageInfo2UsageAdapter> {
  private final GlobalSearchScope myScope;

  FileAndLineTextRenderer(@NotNull GlobalSearchScope scope) {
    myScope = scope;
  }

  @Override
  protected void customizeCellRenderer(@NotNull JList<? extends UsageInfo2UsageAdapter> list,
                                       @NotNull UsageInfo2UsageAdapter value, int index, boolean selected, boolean hasFocus) {
    TextChunk[] text = value.getPresentation().getText();
    // line number / file info
    String uniqueVirtualFilePath = SlowOperations.allowSlowOperations(() -> getFilePath(value, myScope));
    VirtualFile prevFile = findPrevFile(list, index);
    SimpleTextAttributes attributes = Comparing.equal(value.getFile(), prevFile)
                                      ? FindPopupPanel.UsageTableCellRenderer.REPEATED_FILE_ATTRIBUTES
                                      : FindPopupPanel.UsageTableCellRenderer.ORDINAL_ATTRIBUTES;
    append(uniqueVirtualFilePath, attributes);
    if (text.length > 0) append(" " + text[0].getText(), FindPopupPanel.UsageTableCellRenderer.ORDINAL_ATTRIBUTES);
    setBorder(null);
  }

  @NotNull
  @NlsSafe
  private static String getFilePath(@NotNull UsageInfo2UsageAdapter usageAdapter, @NotNull GlobalSearchScope scope) {
    VirtualFile file = usageAdapter.getFile();
    if (file == null) return "";

    Project project = usageAdapter.getUsageInfo().getProject();
    return ScratchUtil.isScratch(file)
           ? ScratchUtil.getRelativePath(project, file)
           : UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(project, file, scope);
  }

  @Nullable
  private static VirtualFile findPrevFile(@NotNull JList<? extends UsageInfo2UsageAdapter> list, int index) {
    if (index <= 0) return null;
    Object prev = list.getModel().getElementAt(index - 1);
    //noinspection ConstantConditions,CastCanBeRemovedNarrowingVariableType
    return prev instanceof UsageInfo2UsageAdapter ? ((UsageInfo2UsageAdapter)prev).getFile() : null;
  }
}
