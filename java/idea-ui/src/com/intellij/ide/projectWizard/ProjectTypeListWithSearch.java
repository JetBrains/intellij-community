// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.util.Function;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;

public class ProjectTypeListWithSearch<T> extends JPanel {
  public ProjectTypeListWithSearch(@NotNull JBList<T> list, @NotNull JScrollPane scrollPane, @NotNull Function<? super T, String> namer) {
    super(new BorderLayout());

    list.getEmptyText().setText(UIBundle.message("message.noMatchesFound"));

    SearchTextField searchTextField = new SearchTextField();
    add(searchTextField, BorderLayout.NORTH);
    add(scrollPane, BorderLayout.CENTER);

    scrollPane.setBorder(JBUI.Borders.empty());

    SpeedSearch speedSearch = new SpeedSearch();
    speedSearch.setEnabled(true);

    list.addKeyListener(speedSearch);

    int selectedIndex = list.getSelectedIndex();
    int modelSize = list.getModel().getSize();
    NameFilteringListModel<T> model = new NameFilteringListModel<>(
      list.getModel(), namer, speedSearch::shouldBeShowing,
      () -> StringUtil.notNullize(speedSearch.getFilter()));
    list.setModel(model);

    if (model.getSize() == modelSize) {
      list.setSelectedIndex(selectedIndex);
    }

    searchTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        speedSearch.updatePattern(searchTextField.getText());
        model.refilter();
        list.setSelectedIndex(0);
      }
    });
  }
}