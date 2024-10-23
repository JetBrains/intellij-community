// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actionsOnSave;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.hover.TableHoverListener;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public final class ActionsOnSaveConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  public static final class ActionsOnSaveConfigurableProvider extends ConfigurableProvider {
    private final @NotNull Project myProject;

    public ActionsOnSaveConfigurableProvider(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public @Nullable Configurable createConfigurable() {
      return new ActionsOnSaveConfigurable(myProject);
    }
  }

  public static final String CONFIGURABLE_ID = "actions.on.save";

  private static final Logger LOG = Logger.getInstance(ActionsOnSaveConfigurable.class);

  private final @NotNull Project myProject;
  private final @NotNull Disposable myDisposable;

  private ActionOnSaveContext myActionOnSaveContext;
  private TableView<ActionOnSaveInfo> myTable;

  public ActionsOnSaveConfigurable(@NotNull Project project) {
    myProject = project;
    myDisposable = Disposer.newDisposable();
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("actions.on.save.page.title");
  }

  @Override
  public @NotNull String getId() {
    return CONFIGURABLE_ID;
  }

  @Override
  public String getHelpTopic() {
    return "settings.actions.on.save";
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

  private @NotNull TableView<ActionOnSaveInfo> createTable() {
    TableView<ActionOnSaveInfo> table = new TableView<>(new ListTableModel<>(new ActionOnSaveColumnInfo(), new ActivatedOnColumnInfo()));
    table.getTableHeader().setReorderingAllowed(false);
    table.setShowGrid(false);
    table.setRowSelectionAllowed(false);
    table.addAncestorListener(new AncestorListenerAdapter() {
      @Override
      public void ancestorAdded(AncestorEvent event) {
        // The 'Actions on Save' page has become visible, either the first time (right after createComponent()), or after switching to some
        // other page in Settings and then back to this page. Need to update the table.
        updateTable();
      }
    });

    // Table cells contain ActionLinks and DropDownLinks. They should get underlined on hover, and should handle clicks. In order to have
    // actionable UI inside cells, table switches from renderer to editor for the currently hovered cell as mouse pointer moves.
    // TableCellRenderer and TableCellEditor return the same component, so users don't notice cell editing start/stop.
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
    for (ActionOnSaveInfo info : myTable.getItems()) {
      if (info.isModified()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    for (ActionOnSaveInfo info : myTable.getItems()) {
      info.apply();
    }
  }

  @Override
  public void reset() {
    if (!myTable.isShowing()) {
      // Settings.KEY.getData(...) is null at this point, so we'd rather initialize a bit later.
      // This method will be called again in the next UI cycle from the AncestorListener registered in the createTable() method above.
      return;
    }

    for (ActionOnSaveInfo info : myTable.getItems()) {
      if (info instanceof ActionOnSaveBackedByOwnConfigurable<?>) {
        ((ActionOnSaveBackedByOwnConfigurable<?>)info).resetUiOnOwnPageThatIsMirroredOnActionsOnSavePage();
      }
    }

    myActionOnSaveContext = null;
    updateTable();
  }

  private void updateTable() {
    Settings settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(myTable));
    if (settings == null) {
      myTable.getListTableModel().setItems(new ArrayList<>());
      LOG.error("Settings not found");
      return;
    }

    if (myActionOnSaveContext == null) {
      myActionOnSaveContext = new ActionOnSaveContext(myProject, settings, myDisposable);
    }

    ArrayList<ActionOnSaveInfo> infos = ActionOnSaveInfoProvider.getAllActionOnSaveInfos(myActionOnSaveContext);
    myTable.getListTableModel().setItems(infos);
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myDisposable);
  }

  public static @NotNull ActionLink createGoToActionsOnSavePageLink() {
    return createGoToPageInSettingsLink(IdeBundle.message("actions.on.save.link.all.actions.on.save"), CONFIGURABLE_ID);
  }

  public static @NotNull ActionLink createGoToPageInSettingsLink(@NotNull @NlsContexts.LinkLabel String linkText,
                                                                 @NotNull String configurableId) {
    return new ActionLink(linkText, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        DataManager.getInstance().getDataContextFromFocusAsync().onSuccess(context -> {
          Settings settings = Settings.KEY.getData(context);
          if (settings != null) {
            settings.select(settings.find(configurableId));
          }
        });
      }
    });
  }
}
