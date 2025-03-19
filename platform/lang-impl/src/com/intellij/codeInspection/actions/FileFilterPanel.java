// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionListener;

/**
 * @author Dmitry Avdeev
 */
final class FileFilterPanel {
  private final JCheckBox myUseFileMask = new JCheckBox();
  private final JComboBox<String> myFileMask = new ComboBox<>();
  private final JPanel myPanel = new FileFilterPanelUi().panel(myUseFileMask, myFileMask);

  void init(AnalysisUIOptions options) {
    FindInProjectUtil.initFileFilter(myFileMask, myUseFileMask);
    myUseFileMask.setSelected(StringUtil.isNotEmpty(options.FILE_MASK));
    myFileMask.setEnabled(StringUtil.isNotEmpty(options.FILE_MASK));
    myFileMask.setSelectedItem(options.FILE_MASK);
    ActionListener listener = __ -> options.FILE_MASK = myUseFileMask.isSelected() ? (String)myFileMask.getSelectedItem() : null;
    myUseFileMask.addActionListener(listener);
    myFileMask.addActionListener(listener);
  }

  @Nullable
  GlobalSearchScope getSearchScope() {
    if (!myUseFileMask.isSelected()) return null;
    String text = (String)myFileMask.getSelectedItem();
    if (text == null) return null;

    final Condition<CharSequence> patternCondition = FindInProjectUtil.createFileMaskCondition(text);
    return new GlobalSearchScope() {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        return patternCondition.value(file.getNameSequence());
      }

      @Override
      public boolean isSearchInModuleContent(@NotNull Module aModule) {
        return true;
      }

      @Override
      public boolean isSearchInLibraries() {
        return true;
      }
    };
  }
  
  JPanel getPanel() {
    return myPanel;
  }
}
