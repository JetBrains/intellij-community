// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.LocalPathCellEditor;
import com.intellij.util.ui.table.IconTableCellRenderer;
import com.intellij.util.ui.table.TableModelEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.UUID;

import static com.intellij.util.ui.table.TableModelEditor.EditableColumnInfo;

final class BrowserSettingsPanel {
  private static final FileChooserDescriptor APP_FILE_CHOOSER_DESCRIPTOR = FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor();

  private static final EditableColumnInfo<ConfigurableWebBrowser, String> PATH_COLUMN_INFO =
    new EditableColumnInfo<>(IdeBundle.message("settings.browsers.column.path")) {
      @Override
      public String valueOf(ConfigurableWebBrowser item) {
        return PathUtil.toSystemDependentName(item.getPath());
      }

      @Override
      public void setValue(ConfigurableWebBrowser item, String value) {
        item.setPath(value);
      }

      @Nullable
      @Override
      public TableCellEditor getEditor(ConfigurableWebBrowser item) {
        return new LocalPathCellEditor().fileChooserDescriptor(APP_FILE_CHOOSER_DESCRIPTOR).normalizePath(true);
      }
    };

  private static final EditableColumnInfo<ConfigurableWebBrowser, Boolean> ACTIVE_COLUMN_INFO = new EditableColumnInfo<>() {
    @Override
    public Class getColumnClass() {
      return Boolean.class;
    }

    @Override
    public Boolean valueOf(ConfigurableWebBrowser item) {
      return item.isActive();
    }

    @Override
    public void setValue(ConfigurableWebBrowser item, Boolean value) {
      item.setActive(value);
    }
  };

  private static final ColumnInfo[] COLUMNS = {ACTIVE_COLUMN_INFO,
    new EditableColumnInfo<ConfigurableWebBrowser, String>(IdeBundle.message("settings.browsers.column.name")) {
      @Override
      public String valueOf(ConfigurableWebBrowser item) {
        return item.getName();
      }

      @Override
      public void setValue(ConfigurableWebBrowser item, String value) {
        item.setName(value);
      }
    },
    new ColumnInfo<ConfigurableWebBrowser, BrowserFamily>(IdeBundle.message("settings.browsers.column.family")) {
      @Override
      public Class getColumnClass() {
        return BrowserFamily.class;
      }

      @Override
      public BrowserFamily valueOf(ConfigurableWebBrowser item) {
        return item.getFamily();
      }

      @Override
      public void setValue(ConfigurableWebBrowser item, BrowserFamily value) {
        item.setFamily(value);
        item.setSpecificSettings(value.createBrowserSpecificSettings());
      }

      @NotNull
      @Override
      public TableCellRenderer getRenderer(ConfigurableWebBrowser item) {
        return IconTableCellRenderer.ICONABLE;
      }

      @Override
      public boolean isCellEditable(ConfigurableWebBrowser item) {
        return !WebBrowserManager.getInstance().isPredefinedBrowser(item);
      }
    },
    PATH_COLUMN_INFO};

  private JPanel root;

  private TextFieldWithBrowseButton alternativeBrowserPathField;

  private JPanel defaultBrowserPanel;

  @SuppressWarnings("UnusedDeclaration")
  private JComponent browsersTable;

  private ComboBox<DefaultBrowserPolicy> defaultBrowserPolicyComboBox;
  private JBCheckBox showBrowserHover;
  private JBCheckBox showBrowserHoverXml;
  private JPanel browserPopupPanel;

  private TableModelEditor<ConfigurableWebBrowser> browsersEditor;

  private String customPathValue;

  BrowserSettingsPanel() {
    alternativeBrowserPathField.addBrowseFolderListener(IdeBundle.message("title.select.path.to.browser"), null, null, APP_FILE_CHOOSER_DESCRIPTOR);
    defaultBrowserPanel.setBorder(TitledSeparator.createEmptyBorder());

    ArrayList<DefaultBrowserPolicy> defaultBrowserPolicies = new ArrayList<>();
    if (BrowserLauncherAppless.canUseSystemDefaultBrowserPolicy()) {
      defaultBrowserPolicies.add(DefaultBrowserPolicy.SYSTEM);
    }
    defaultBrowserPolicies.add(DefaultBrowserPolicy.FIRST);
    defaultBrowserPolicies.add(DefaultBrowserPolicy.ALTERNATIVE);

    defaultBrowserPolicyComboBox.setModel(new CollectionComboBoxModel<>(defaultBrowserPolicies));
    defaultBrowserPolicyComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(@NotNull ItemEvent e) {
        boolean customPathEnabled = e.getItem() == DefaultBrowserPolicy.ALTERNATIVE;
        if (e.getStateChange() == ItemEvent.DESELECTED) {
          if (customPathEnabled) {
            customPathValue = alternativeBrowserPathField.getText();
          }
        }
        else if (e.getStateChange() == ItemEvent.SELECTED) {
          alternativeBrowserPathField.setEnabled(customPathEnabled);
          updateCustomPathTextFieldValue((DefaultBrowserPolicy)e.getItem());
        }
      }
    });

    defaultBrowserPolicyComboBox.setRenderer(SimpleListCellRenderer.create("", value -> {
      String text = value == DefaultBrowserPolicy.SYSTEM ? IdeBundle.message("settings.browsers.system.default") :
                    value == DefaultBrowserPolicy.FIRST ? IdeBundle.message("settings.browsers.first.listed") :
                    value == DefaultBrowserPolicy.ALTERNATIVE ? IdeBundle.message("settings.browsers.custom.path") :
                    null;
      if (text == null) throw new IllegalStateException(String.valueOf(value));
      return text;
    }));

    browserPopupPanel.setBorder(IdeBorderFactory.createTitledBorder(IdeBundle.message("settings.browsers.show.browser.popup.in.the.editor")));
  }

  private void updateCustomPathTextFieldValue(@NotNull DefaultBrowserPolicy browser) {
    if (browser == DefaultBrowserPolicy.ALTERNATIVE) {
      alternativeBrowserPathField.setText(customPathValue);
    }
    else if (browser == DefaultBrowserPolicy.FIRST) {
      setCustomPathToFirstListed();
    }
    else {
      alternativeBrowserPathField.setText("");
    }
  }

  private void createUIComponents() {
    TableModelEditor.DialogItemEditor<ConfigurableWebBrowser> itemEditor = new TableModelEditor.DialogItemEditor<>() {
      @NotNull
      @Override
      public Class<ConfigurableWebBrowser> getItemClass() {
        return ConfigurableWebBrowser.class;
      }

      @Override
      public ConfigurableWebBrowser clone(@NotNull ConfigurableWebBrowser item, boolean forInPlaceEditing) {
        return new ConfigurableWebBrowser(forInPlaceEditing ? item.getId() : UUID.randomUUID(),
                                          item.getFamily(), item.getName(), item.getPath(), item.isActive(),
                                          forInPlaceEditing ? item.getSpecificSettings() : cloneSettings(item));
      }

      @Override
      public void edit(@NotNull ConfigurableWebBrowser browser,
                       @NotNull Function<? super ConfigurableWebBrowser, ? extends ConfigurableWebBrowser> mutator,
                       boolean isAdd) {
        BrowserSpecificSettings settings = cloneSettings(browser);
        if (settings != null && ShowSettingsUtil.getInstance().editConfigurable(browsersTable, settings.createConfigurable())) {
          mutator.fun(browser).setSpecificSettings(settings);
        }
      }

      @Nullable
      private BrowserSpecificSettings cloneSettings(@NotNull ConfigurableWebBrowser browser) {
        BrowserSpecificSettings settings = browser.getSpecificSettings();
        if (settings == null) {
          return null;
        }

        BrowserSpecificSettings newSettings = browser.getFamily().createBrowserSpecificSettings();
        assert newSettings != null;
        TableModelEditor.cloneUsingXmlSerialization(settings, newSettings);
        return newSettings;
      }

      @Override
      public void applyEdited(@NotNull ConfigurableWebBrowser oldItem, @NotNull ConfigurableWebBrowser newItem) {
        oldItem.setSpecificSettings(newItem.getSpecificSettings());
      }

      @Override
      public boolean isEditable(@NotNull ConfigurableWebBrowser browser) {
        return browser.getSpecificSettings() != null;
      }

      @Override
      public boolean isRemovable(@NotNull ConfigurableWebBrowser item) {
        return !WebBrowserManager.getInstance().isPredefinedBrowser(item);
      }
    };
    browsersEditor = new TableModelEditor<>(COLUMNS, itemEditor, IdeBundle.message("settings.browsers.no.web.browsers.configured"))
      .modelListener(new TableModelEditor.DataChangedListener<>() {
        @Override
        public void tableChanged(@NotNull TableModelEvent event) {
          update();
        }

        @Override
        public void dataChanged(@NotNull ColumnInfo<ConfigurableWebBrowser, ?> columnInfo, int rowIndex) {
          if (columnInfo == PATH_COLUMN_INFO || columnInfo == ACTIVE_COLUMN_INFO) {
            update();
          }
        }

        private void update() {
          if (getDefaultBrowser() == DefaultBrowserPolicy.FIRST) {
            setCustomPathToFirstListed();
          }
        }
      });
    browsersTable = browsersEditor.createComponent();
  }

  private void setCustomPathToFirstListed() {
    ListTableModel<ConfigurableWebBrowser> model = browsersEditor.getModel();
    for (int i = 0, n = model.getRowCount(); i < n; i++) {
      ConfigurableWebBrowser browser = model.getRowValue(i);
      if (browser.isActive() && browser.getPath() != null) {
        alternativeBrowserPathField.setText(browser.getPath());
        return;
      }
    }

    alternativeBrowserPathField.setText("");
  }

  @NotNull
  public JPanel getComponent() {
    return root;
  }

  public boolean isModified() {
    WebBrowserManager browserManager = WebBrowserManager.getInstance();
    GeneralSettings generalSettings = GeneralSettings.getInstance();

    DefaultBrowserPolicy defaultBrowserPolicy = getDefaultBrowser();
    if (getDefaultBrowserPolicy(browserManager) != defaultBrowserPolicy ||
        browserManager.isShowBrowserHover() != showBrowserHover.isSelected() ||
        browserManager.isShowBrowserHoverXml() != showBrowserHoverXml.isSelected()) {
      return true;
    }

    if (defaultBrowserPolicy == DefaultBrowserPolicy.ALTERNATIVE &&
        !Comparing.strEqual(generalSettings.getBrowserPath(), alternativeBrowserPathField.getText())) {
      return true;
    }

    return browsersEditor.isModified();
  }

  public void apply() {
    GeneralSettings settings = GeneralSettings.getInstance();

    settings.setUseDefaultBrowser(getDefaultBrowser() == DefaultBrowserPolicy.SYSTEM);

    if (alternativeBrowserPathField.isEnabled()) {
      settings.setBrowserPath(alternativeBrowserPathField.getText());
    }

    WebBrowserManager browserManager = WebBrowserManager.getInstance();
    browserManager.setShowBrowserHover(showBrowserHover.isSelected());
    browserManager.setShowBrowserHoverXml(showBrowserHoverXml.isSelected());
    browserManager.defaultBrowserPolicy = getDefaultBrowser();
    browserManager.setList(browsersEditor.apply());
  }

  private DefaultBrowserPolicy getDefaultBrowser() {
    return (DefaultBrowserPolicy)defaultBrowserPolicyComboBox.getSelectedItem();
  }

  public void reset() {
    final WebBrowserManager browserManager = WebBrowserManager.getInstance();
    DefaultBrowserPolicy effectiveDefaultBrowserPolicy = getDefaultBrowserPolicy(browserManager);
    defaultBrowserPolicyComboBox.setSelectedItem(effectiveDefaultBrowserPolicy);

    GeneralSettings settings = GeneralSettings.getInstance();
    showBrowserHover.setSelected(browserManager.isShowBrowserHover());
    showBrowserHoverXml.setSelected(browserManager.isShowBrowserHoverXml());
    browsersEditor.reset(browserManager.getList());

    customPathValue = settings.getBrowserPath();
    alternativeBrowserPathField.setEnabled(effectiveDefaultBrowserPolicy == DefaultBrowserPolicy.ALTERNATIVE);
    updateCustomPathTextFieldValue(effectiveDefaultBrowserPolicy);
  }

  private static DefaultBrowserPolicy getDefaultBrowserPolicy(WebBrowserManager manager) {
    DefaultBrowserPolicy policy = manager.getDefaultBrowserPolicy();
    if (policy != DefaultBrowserPolicy.SYSTEM || BrowserLauncherAppless.canUseSystemDefaultBrowserPolicy()) {
      return policy;
    }
    // if system default browser policy cannot be used
    return DefaultBrowserPolicy.ALTERNATIVE;
  }

  public void selectBrowser(@NotNull WebBrowser browser) {
    if (browser instanceof ConfigurableWebBrowser) {
      browsersEditor.selectItem((ConfigurableWebBrowser)browser);
    }
  }
}