// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionsOnSave;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actionsOnSave.api.ActionOnSaveInfo;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ActionsOnSaveConfigurable implements SearchableConfigurable, Configurable.NoScroll {
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
    JPanel panel = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
    panel.add(new JBScrollPane(createTable()), BorderLayout.CENTER);
    panel.add(createAutoSaveOptionsLink(), BorderLayout.SOUTH);
    return panel;
  }

  private static TableView<ActionOnSaveInfo> createTable() {
    return new TableView<>(new ListTableModel<>(createActionOnSaveColumn(), createActivatedOnColumn()));
  }

  private static ColumnInfo<ActionOnSaveInfo, ActionOnSaveInfo> createActionOnSaveColumn() {
    return new ColumnInfo<>(IdeBundle.message("actions.on.save.table.column.name.action")) {
      @Override
      public @Nullable ActionOnSaveInfo valueOf(ActionOnSaveInfo info) {
        return info;
      }
    };
  }

  private static ColumnInfo<ActionOnSaveInfo, ActionOnSaveInfo> createActivatedOnColumn() {
    return new ColumnInfo<>(IdeBundle.message("actions.on.save.table.column.name.activated.on")) {
      @Override
      public String getMaxStringValue() {
        // Affects column width
        return "Explicit save (Ctrl + S)  []";
      }

      @Override
      public @Nullable ActionOnSaveInfo valueOf(ActionOnSaveInfo info) {
        return info;
      }
    };
  }

  @NotNull
  private static ActionLink createAutoSaveOptionsLink() {
    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        DataManager.getInstance().getDataContextFromFocusAsync().onSuccess(context -> {
          Settings settings = Settings.KEY.getData(context);
          if (settings != null) {
            settings.select(settings.find("preferences.general"));
          }
        });
      }
    };

    return new ActionLink(IdeBundle.message("actions.on.save.link.configure.autosave.options"), listener);
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
  }
}
