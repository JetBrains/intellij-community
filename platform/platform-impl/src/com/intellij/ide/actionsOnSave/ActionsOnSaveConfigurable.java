// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionsOnSave;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actionsOnSave.api.ActionOnSaveInfo;
import com.intellij.ide.actionsOnSave.api.ActionOnSaveInfoProvider;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.hover.TableHoverListener;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ActionsOnSaveConfigurable implements SearchableConfigurable, Configurable.NoScroll {

  private final @NotNull Project myProject;

  private TableView<ActionOnSaveInfo> myTable;

  public ActionsOnSaveConfigurable(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("actions.on.save.page.title");
  }

  @Override
  public @NotNull String getId() {
    return "actions.on.save";
  }

  @Override
  public @Nullable JComponent createComponent() {
    myTable = createTable();

    JPanel panel = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
    panel.add(new JBScrollPane(myTable), BorderLayout.CENTER);

    ActionLink autoSaveOptionsLink =
      createGoToPageInSettingsLink(IdeBundle.message("actions.on.save.link.configure.autosave.options"), "preferences.general");
    panel.add(autoSaveOptionsLink, BorderLayout.SOUTH);

    return panel;
  }

  private static @NotNull TableView<ActionOnSaveInfo> createTable() {
    TableView<ActionOnSaveInfo> table = new TableView<>(new ListTableModel<>(new ActionOnSaveColumnInfo(), new ActivatedOnColumnInfo()));
    table.getTableHeader().setReorderingAllowed(false);
    table.setShowGrid(false);
    table.setRowSelectionAllowed(false);

    // Table cells contain ActionLinks and DropDownLinks. They should get underlined on hover, and should handle clicks. In order to have
    // actionable UI inside cells, table switches from renderer to editor for the currently hovered cell as mouse pointer moves.
    // TableCellRenderer) and TableCellEditor return the same component, so users don't notice cell editing start/stop.
    new TableHoverListener() {
      @Override
      public void onHover(@NotNull JTable table, int row, int column) {
        if (column != -1 && row != -1) {
          table.editCellAt(row, column);
        }
        else {
          ((TableView<?>)table).stopEditing();
        }
      }
    }.addTo(table);

    return table;
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
  }

  @Override
  public void reset() {
    myTable.getListTableModel().setItems(ActionOnSaveInfoProvider.getAllActionOnSaveInfos(myProject));
  }

  public static @NotNull ActionLink createGoToPageInSettingsLink(@NotNull String pageId) {
    return createGoToPageInSettingsLink(IdeBundle.message("actions.on.save.link.configure"), pageId);
  }

  public static @NotNull ActionLink createGoToPageInSettingsLink(@NotNull @NlsContexts.LinkLabel String linkText, @NotNull String pageId) {
    return new ActionLink(linkText, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        DataManager.getInstance().getDataContextFromFocusAsync().onSuccess(context -> {
          Settings settings = Settings.KEY.getData(context);
          if (settings != null) {
            settings.select(settings.find(pageId));
          }
        });
      }
    });
  }
}
