// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.ListIterator;

/**
 * @author Alexander Lobas
 */
public class ListPluginComponent extends CellPluginComponent {
  public static final Color DisabledColor = JBColor.namedColor("Plugins.disabledForeground", new JBColor(0xB1B1B1, 0x696969));

  private final MyPluginModel myPluginModel;
  private boolean myUninstalled;

  private JLabel myVersion;
  private JLabel myLastUpdated;
  public JButton myUpdateButton;
  private final JCheckBox myEnableDisableButton = new JCheckBox();
  private RestartButton myRestartButton;
  private final BaselinePanel myBaselinePanel = new BaselinePanel();
  private ProgressIndicatorEx myIndicator;

  private IdeaPluginDescriptor myUpdateDescriptor;

  public ListPluginComponent(@NotNull MyPluginModel pluginModel, @NotNull IdeaPluginDescriptor plugin, boolean pluginForUpdate) {
    super(plugin);
    myPluginModel = pluginModel;
    pluginModel.addComponent(this);

    setFocusable(true);
    myEnableDisableButton.setFocusable(false);

    setOpaque(true);
    setLayout(new BorderLayout(JBUIScale.scale(8), 0));
    setBorder(JBUI.Borders.empty(5, 10, 10, 10));

    createButtons(pluginForUpdate);

    if (pluginForUpdate) {
      addIconComponent(this, BorderLayout.WEST);
    }
    else {
      JPanel westPanel = new NonOpaquePanel(createCheckboxIconLayout());
      westPanel.setBorder(JBUI.Borders.emptyTop(5));
      westPanel.add(myEnableDisableButton);
      addIconComponent(westPanel, null);
      add(westPanel, BorderLayout.WEST);
    }

    JPanel centerPanel = new NonOpaquePanel(new VerticalLayout(0));
    add(centerPanel);

    centerPanel.add(myBaselinePanel, VerticalLayout.FILL_HORIZONTAL);

    addNameComponent(myBaselinePanel);
    myName.setVerticalAlignment(SwingConstants.TOP);

    createVersion(pluginForUpdate);
    updateErrors();

    if (!pluginForUpdate) {
      addDescriptionComponent(centerPanel, PluginManagerConfigurableNew.getShortDescription(plugin, false), new LineFunction(1, true));
    }

    if (MyPluginModel.isInstallingOrUpdate(plugin)) {
      showProgress(false);
    }

    updateColors(EventHandler.SelectionType.NONE);
  }

  private void createButtons(boolean update) {
    myEnableDisableButton.setOpaque(false);

    if (myPlugin instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)myPlugin).isDeleted()) {
      myBaselinePanel.addButtonComponent(myRestartButton = new RestartButton(myPluginModel));

      myEnableDisableButton.setSelected(false);
      myEnableDisableButton.setEnabled(false);
      myEnableDisableButton.setVisible(false);

      myUninstalled = true;
    }
    else {
      InstalledPluginsState pluginsState = InstalledPluginsState.getInstance();
      PluginId id = myPlugin.getPluginId();

      if (pluginsState.wasInstalled(id) || pluginsState.wasUpdated(id)) {
        myBaselinePanel.addButtonComponent(myRestartButton = new RestartButton(myPluginModel));
      }
      else if (update) {
        myUpdateButton = new UpdateButton();
        myUpdateButton.addActionListener(e -> myPluginModel.installOrUpdatePlugin(myPlugin, myPlugin));
        myBaselinePanel.addButtonComponent(myUpdateButton);
      }

      myEnableDisableButton.setSelected(isEnabledState());
      myEnableDisableButton.addActionListener(e -> myPluginModel.changeEnableDisable(myPlugin));
    }
  }

  @Override
  public void showProgress() {
    showProgress(true);
  }

  private void showProgress(boolean repaint) {
    myEnableDisableButton.setSelected(false);
    myEnableDisableButton.setEnabled(false);

    OneLineProgressIndicator indicator = new OneLineProgressIndicator();
    indicator.setCancelRunnable(() -> myPluginModel.finishInstall(myPlugin, false, false));
    myBaselinePanel.setProgressComponent(this, indicator.createBaselineWrapper());
    MyPluginModel.addProgress(myPlugin, indicator);
    myIndicator = indicator;

    if (repaint) {
      fullRepaint();
    }
  }

  @Override
  public void hideProgress(boolean success) {
    myIndicator = null;
    myEnableDisableButton.setEnabled(true);

    myBaselinePanel.removeProgressComponent();
    if (success) {
      enableRestart();
    }
    fullRepaint();
  }

  @Override
  public void clearProgress() {
    myIndicator = null;
  }

  @NotNull
  private static AbstractLayoutManager createCheckboxIconLayout() {
    return new AbstractLayoutManager() {
      final JBValue offset = new JBValue.Float(12);

      @Override
      public Dimension preferredLayoutSize(Container parent) {
        Dimension size = new Dimension();

        if (parent.getComponentCount() == 2) {
          Dimension iconSize = parent.getComponent(1).getPreferredSize();
          size.width = parent.getComponent(0).getPreferredSize().width + offset.get() + iconSize.width;
          size.height = iconSize.height;
        }

        JBInsets.addTo(size, parent.getInsets());
        return size;
      }

      @Override
      public void layoutContainer(Container parent) {
        if (parent.getComponentCount() == 2) {
          Component checkBox = parent.getComponent(0);
          Component icon = parent.getComponent(1);

          Dimension checkBoxSize = checkBox.getPreferredSize();
          Dimension iconSize = icon.getPreferredSize();
          Insets insets = parent.getInsets();
          int x = insets.left;
          int y = insets.top;

          checkBox.setBounds(x, y + (iconSize.height - checkBoxSize.height) / 2, checkBoxSize.width, checkBoxSize.height);
          icon.setBounds(x + checkBoxSize.width + offset.get(), y, iconSize.width, iconSize.height);
        }
      }
    };
  }

  private void createVersion(boolean pluginForUpdate) {
    String version = StringUtil.defaultIfEmpty(myPlugin.getVersion(), null);

    if (version != null && (pluginForUpdate || !myPlugin.isBundled() || myPlugin.allowBundledUpdate())) {
      String oldVersion = null;

      if (pluginForUpdate) {
        IdeaPluginDescriptor installedPlugin = PluginManager.getPlugin(myPlugin.getPluginId());
        oldVersion = installedPlugin == null ? null : StringUtil.defaultIfEmpty(installedPlugin.getVersion(), null);
      }
      if (oldVersion == null) {
        version = "v" + version;
      }
      else {
        version = "Version " + oldVersion + " " + UIUtil.rightArrow() + " " + version;
      }

      myVersion = new JLabel(version);
      myVersion.setOpaque(false);
      myBaselinePanel.addVersionComponent(PluginManagerConfigurableNew.installTiny(myVersion));
    }

    if (myPlugin instanceof PluginNode) {
      String date = PluginManagerConfigurableNew.getLastUpdatedDate(myPlugin);
      if (date != null) {
        myLastUpdated = new JLabel(date, AllIcons.Plugins.Updated, SwingConstants.CENTER);
        myLastUpdated.setOpaque(false);
        myBaselinePanel.addVersionComponent(PluginManagerConfigurableNew.installTiny(myLastUpdated));
      }
    }
  }

  public void setUpdateDescriptor(@Nullable IdeaPluginDescriptor descriptor) {
    if (myUpdateDescriptor == null && descriptor == null) {
      return;
    }

    myUpdateDescriptor = descriptor;

    if (descriptor == null) {
      if (myVersion != null) {
        myVersion.setText("v" + myPlugin.getVersion());
      }
      if (myUpdateButton != null) {
        myUpdateButton.setVisible(false);
      }
    }
    else {
      if (myVersion == null) {
        myVersion = new JLabel();
        myVersion.setOpaque(false);
        myBaselinePanel.addVersionComponent(PluginManagerConfigurableNew.installTiny(myVersion));
      }
      myVersion.setText("Version " + myPlugin.getVersion() + " " + UIUtil.rightArrow() + " " + descriptor.getVersion());

      if (myUpdateButton == null) {
        myUpdateButton = new UpdateButton();
        myUpdateButton.addActionListener(e -> myPluginModel.installOrUpdatePlugin(myPlugin, myUpdateDescriptor));
        myBaselinePanel.addButtonComponent(myUpdateButton);
      }
      else {
        myUpdateButton.setVisible(true);
      }
    }

    myBaselinePanel.doLayout();
  }

  @Override
  public void updateErrors() {
    boolean errors = myPluginModel.hasErrors(myPlugin);
    updateIcon(errors, myUninstalled || !myPluginModel.isEnabled(myPlugin));

    if (errors) {
      Ref<String> enableAction = new Ref<>();
      String message = myPluginModel.getErrorMessage(myPlugin, enableAction);
      myBaselinePanel.addErrorComponents(message, !enableAction.isNull(), () -> myPluginModel.enableRequiredPlugins(myPlugin));
    }
    else {
      myBaselinePanel.removeErrorComponents();
    }
  }

  @Override
  public void setListeners(@NotNull LinkListener<? super IdeaPluginDescriptor> listener,
                           @NotNull LinkListener<String> searchListener,
                           @NotNull EventHandler eventHandler) {
    super.setListeners(listener, searchListener, eventHandler);
    myBaselinePanel.setListeners(eventHandler);
  }

  @Override
  protected void updateColors(@NotNull Color grayedFg, @NotNull Color background) {
    setBackground(background);

    if (mySelection != EventHandler.SelectionType.NONE) {
      Color color = UIManager.getColor("Plugins.selectionForeground");
      if (color != null) {
        if (myVersion != null) {
          myVersion.setForeground(color);
        }
        if (myLastUpdated != null) {
          myLastUpdated.setForeground(color);
        }
        myName.setForeground(color);
        if (myDescription != null) {
          myDescription.setForeground(color);
        }
        return;
      }
    }

    if (myVersion != null) {
      myVersion.setForeground(grayedFg);
    }
    if (myLastUpdated != null) {
      myLastUpdated.setForeground(grayedFg);
    }

    boolean enabled = !myUninstalled && (MyPluginModel.isInstallingOrUpdate(myPlugin) || myPluginModel.isEnabled(myPlugin));
    myName.setForeground(enabled ? null : DisabledColor);

    if (myDescription != null) {
      myDescription.setForeground(enabled ? grayedFg : DisabledColor);
    }
  }

  private boolean isEnabledState() {
    return myPluginModel.isEnabled(myPlugin);
  }

  @Override
  public void updateAfterUninstall() {
    myUninstalled = true;
    updateColors(mySelection);

    myEnableDisableButton.setSelected(false);
    myEnableDisableButton.setEnabled(false);
    myEnableDisableButton.setVisible(false);

    enableRestart();
  }

  @Override
  public void enableRestart() {
    boolean layout = false;

    if (myUpdateButton != null) {
      myBaselinePanel.removeButtonComponent(myUpdateButton);
      myUpdateButton = null;
      layout = true;
    }
    if (myRestartButton == null) {
      myBaselinePanel.addButtonComponent(myRestartButton = new RestartButton(myPluginModel));
      layout = true;
    }

    if (layout) {
      myBaselinePanel.doLayout();
    }
  }

  @Override
  public void updateEnabledState() {
    if (!myUninstalled) {
      myEnableDisableButton.setSelected(isEnabledState());
    }
    updateErrors();
    setSelection(mySelection, false);
  }

  public void updatePlugin() {
    if (myUpdateButton != null) {
      myUpdateButton.doClick();
    }
  }

  @Override
  public void createPopupMenu(@NotNull DefaultActionGroup group, @NotNull List<? extends CellPluginComponent> selection) {
    for (CellPluginComponent component : selection) {
      if (MyPluginModel.isInstallingOrUpdate(component.myPlugin)) {
        return;
      }
    }

    boolean restart = true;
    for (CellPluginComponent component : selection) {
      if (((ListPluginComponent)component).myRestartButton == null) {
        restart = false;
        break;
      }
    }
    if (restart) {
      group.add(new ButtonAnAction(((ListPluginComponent)selection.get(0)).myRestartButton));
      return;
    }

    int size = selection.size();
    JButton[] updateButtons = new JButton[size];

    for (int i = 0; i < size; i++) {
      JButton button = ((ListPluginComponent)selection.get(i)).myUpdateButton;
      if (button == null) {
        updateButtons = null;
        break;
      }
      updateButtons[i] = button;
    }

    if (updateButtons != null) {
      group.add(new ButtonAnAction(updateButtons));
      return;
    }

    Pair<Boolean, IdeaPluginDescriptor[]> result = getSelectionNewState(selection);
    group.add(new MyAnAction(result.first ? "Enable" : "Disable", null, KeyEvent.VK_SPACE) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        myPluginModel.changeEnableDisable(result.second, result.first);
      }
    });

    for (CellPluginComponent component : selection) {
      if (((ListPluginComponent)component).myUninstalled || component.myPlugin.isBundled()) {
        return;
      }
    }

    group.addSeparator();
    group.add(new MyAnAction("Uninstall", IdeActions.ACTION_EDITOR_DELETE, EventHandler.DELETE_CODE) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (!MyPluginModel.showUninstallDialog(selection)) {
          return;
        }
        for (CellPluginComponent component : selection) {
          myPluginModel.doUninstall(component, component.myPlugin, null);
        }
      }
    });
  }

  @Override
  public void handleKeyAction(int keyCode, @NotNull List<? extends CellPluginComponent> selection) {
    for (CellPluginComponent component : selection) {
      if (MyPluginModel.isInstallingOrUpdate(component.myPlugin)) {
        return;
      }
    }

    boolean restart = true;
    for (CellPluginComponent component : selection) {
      if (((ListPluginComponent)component).myRestartButton == null) {
        restart = false;
        break;
      }
    }

    boolean update = true;
    for (CellPluginComponent component : selection) {
      if (((ListPluginComponent)component).myUpdateButton == null) {
        update = false;
        break;
      }
    }

    if (keyCode == KeyEvent.VK_ENTER) {
      if (restart) {
        ((ListPluginComponent)selection.get(0)).myRestartButton.doClick();
      }
      else if (update) {
        for (CellPluginComponent component : selection) {
          ((ListPluginComponent)component).myUpdateButton.doClick();
        }
      }
    }
    else if (!restart && !update) {
      if (keyCode == KeyEvent.VK_SPACE) {
        if (selection.size() == 1) {
          myPluginModel.changeEnableDisable(selection.get(0).myPlugin);
        }
        else {
          Pair<Boolean, IdeaPluginDescriptor[]> result = getSelectionNewState(selection);
          myPluginModel.changeEnableDisable(result.second, result.first);
        }
      }
      else if (keyCode == EventHandler.DELETE_CODE) {
        for (CellPluginComponent component : selection) {
          if (((ListPluginComponent)component).myUninstalled || component.myPlugin.isBundled()) {
            return;
          }
        }
        if (!MyPluginModel.showUninstallDialog(selection)) {
          return;
        }
        for (CellPluginComponent component : selection) {
          myPluginModel.doUninstall(this, component.myPlugin, null);
        }
      }
    }
  }

  @NotNull
  private static Pair<Boolean, IdeaPluginDescriptor[]> getSelectionNewState(@NotNull List<? extends CellPluginComponent> selection) {
    boolean state = ((ListPluginComponent)selection.get(0)).isEnabledState();
    boolean setTrue = false;

    for (ListIterator<? extends CellPluginComponent> I = selection.listIterator(1); I.hasNext(); ) {
      if (state != ((ListPluginComponent)I.next()).isEnabledState()) {
        setTrue = true;
        break;
      }
    }

    int size = selection.size();
    IdeaPluginDescriptor[] plugins = new IdeaPluginDescriptor[size];
    for (int i = 0; i < size; i++) {
      plugins[i] = selection.get(i).myPlugin;
    }

    return Pair.create(setTrue || !state, plugins);
  }

  @Override
  public void close() {
    if (myIndicator != null) {
      MyPluginModel.removeProgress(myPlugin, myIndicator);
      myIndicator = null;
    }
    myPluginModel.removeComponent(this);
  }

  @Override
  public boolean isMarketplace() {
    return false;
  }

  public static class ButtonAnAction extends DumbAwareAction {
    private final JButton[] myButtons;

    ButtonAnAction(@NotNull JButton... buttons) {
      super(buttons[0].getText());
      myButtons = buttons;
      setShortcutSet(CommonShortcuts.ENTER);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      for (JButton button : myButtons) {
        button.doClick();
      }
    }
  }

  public abstract static class MyAnAction extends DumbAwareAction {
    MyAnAction(@Nullable String text, @Nullable String actionId, int keyCode) {
      super(text);
      ShortcutSet shortcutSet = null;
      if (actionId != null) {
        shortcutSet = EventHandler.getShortcuts(actionId);
      }
      if (shortcutSet == null) {
        shortcutSet = new CustomShortcutSet(KeyStroke.getKeyStroke(keyCode, 0));
      }
      setShortcutSet(shortcutSet);
    }
  }
}
