// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.util.Function;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;

public class ProjectTypeListWithSearch<T> extends JPanel {
  public ProjectTypeListWithSearch(@NotNull WizardContext context, @NotNull JBList<T> list, @NotNull JScrollPane scrollPane,
                                   @NotNull Function<? super T, String> namer, @NotNull Runnable showEmptyStatus) {
    super(new BorderLayout());

    SearchTextField searchTextField = new SearchTextField(false);
    searchTextField.getTextEditor().setBorder(JBUI.Borders.empty(2, 5, 2, 0));
    scrollPane.setBorder(JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0));

    add(searchTextField, BorderLayout.NORTH);
    add(scrollPane, BorderLayout.CENTER);

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
        if (model.getSize() == 0) {
          showEmptyStatus.run();
        }
        NewProjectWizardCollector.logSearchChanged(context, searchTextField.getText().length(), model.getSize());
      }
    });

    StatusText emptyText = list.getEmptyText();
    emptyText.setText(IdeBundle.message("plugins.configurable.nothing.found"));
    emptyText.appendSecondaryText(IdeBundle.message("plugins.configurable.search.in.marketplace"),
                                  SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                                  e -> {
                                    ShowSettingsUtil.getInstance().showSettingsDialog(
                                      ProjectManager.getInstance().getDefaultProject(),
                                      PluginManagerConfigurable.class,
                                      configurable -> configurable.openMarketplaceTab(searchTextField.getText()));
                                  });
  }
}