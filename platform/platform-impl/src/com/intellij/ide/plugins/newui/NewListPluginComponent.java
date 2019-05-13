// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ButtonUI;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.ListIterator;

/**
 * @author Alexander Lobas
 */
public class NewListPluginComponent extends CellPluginComponent {
  private final MyPluginModel myPluginModel;
  private final boolean myMarketplace;
  private boolean myUninstalled;
  public IdeaPluginDescriptor myUpdateDescriptor;

  private final JLabel myNameComponent = new JLabel();
  private final JLabel myIconComponent = new JLabel(AllIcons.Plugins.PluginLogo_40);
  private final BaselinePanel myNameAndButtons = new BaselinePanel();
  private final JPanel myCenterPanel;
  public JButton myRestartButton;
  public JButton myInstallButton;
  public JButton myUpdateButton;
  public JCheckBox myEnableDisableButton;
  private JLabel myRating;
  private JLabel myDownloads;
  private JLabel myVersion;
  private JLabel myVendor;
  private JPanel myErrorPanel;
  private JComponent myErrorComponent;
  private OneLineProgressIndicator myIndicator;
  private EventHandler myEventHandler;

  public NewListPluginComponent(@NotNull MyPluginModel pluginModel, @NotNull IdeaPluginDescriptor plugin, boolean marketplace) {
    super(plugin);
    myPluginModel = pluginModel;
    myMarketplace = marketplace;
    pluginModel.addComponent(this);

    setOpaque(true);
    setBorder(JBUI.Borders.empty(10));
    setLayout(new BorderLayout(JBUI.scale(10), 0));

    myIconComponent.setVerticalAlignment(SwingConstants.TOP);
    myIconComponent.setOpaque(false);
    add(myIconComponent, BorderLayout.WEST);

    myNameComponent.setText(myPlugin.getName());
    myNameComponent.setToolTipText(myPlugin.getName());
    myNameAndButtons.add(RelativeFont.BOLD.install(myNameComponent));

    createButtons();

    myCenterPanel = new NonOpaquePanel(new VerticalLayout(0));
    myCenterPanel.add(myNameAndButtons, VerticalLayout.FILL_HORIZONTAL);
    createMetricsPanel(myCenterPanel);
    add(myCenterPanel);

    if (marketplace) {
      updateIcon(false, false);
    }
    else {
      updateErrors();
    }
    if (MyPluginModel.isInstallingOrUpdate(myPlugin)) {
      showProgress(false);
    }
    updateColors(EventHandler.SelectionType.NONE);
  }

  private void createButtons() {
    if (myMarketplace) {
      if (InstalledPluginsState.getInstance().wasInstalled(myPlugin.getPluginId())) {
        myNameAndButtons.addButtonComponent(myRestartButton = new RestartButton(myPluginModel));
      }
      else {
        myNameAndButtons.addButtonComponent(myInstallButton = new InstallButton(false));

        myInstallButton.addActionListener(e -> myPluginModel.installOrUpdatePlugin(myPlugin, null));
        myInstallButton.setEnabled(PluginManager.getPlugin(myPlugin.getPluginId()) == null);
        ColorButton.setWidth72(myInstallButton);
      }
    }
    else if (myPlugin instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)myPlugin).isDeleted()) {
      myNameAndButtons.addButtonComponent(myRestartButton = new RestartButton(myPluginModel));

      myUninstalled = true;
    }
    else {
      InstalledPluginsState pluginsState = InstalledPluginsState.getInstance();
      PluginId id = myPlugin.getPluginId();

      if (pluginsState.wasInstalled(id) || pluginsState.wasUpdated(id)) {
        myNameAndButtons.addButtonComponent(myRestartButton = new RestartButton(myPluginModel));
      }
      else {
        myNameAndButtons.addButtonComponent(myEnableDisableButton = new JCheckBox() {
          int myBaseline = -1;

          @Override
          public int getBaseline(int width, int height) {
            if (myBaseline == -1) {
              JCheckBox checkBox = new JCheckBox("Foo", true);
              Dimension size = checkBox.getPreferredSize();
              myBaseline = checkBox.getBaseline(size.width, size.height) - JBUI.scale(1);
            }
            return myBaseline;
          }

          @Override
          public void setUI(ButtonUI ui) {
            myBaseline = -1;
            super.setUI(ui);
          }

          @Override
          public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            int scale = JBUI.scale(2);
            return new Dimension(size.width + scale, size.height + scale);
          }
        });

        myEnableDisableButton.setOpaque(false);
        myEnableDisableButton.setSelected(isEnabledState());
        myEnableDisableButton.addActionListener(e -> myPluginModel.changeEnableDisable(myPlugin));
      }
    }
  }

  private void createMetricsPanel(@NotNull JPanel centerPanel) {
    JPanel panel = new NonOpaquePanel(new TextHorizontalLayout(JBUI.scale(7)));
    centerPanel.add(panel);

    if (myMarketplace) {
      String rating = PluginManagerConfigurableNew.getRating(myPlugin);
      if (rating != null) {
        myRating = GridCellPluginComponent.createRatingLabel(panel, rating, AllIcons.Plugins.Rating);
      }

      String downloads = PluginManagerConfigurableNew.getDownloads(myPlugin);
      if (downloads != null) {
        myDownloads = GridCellPluginComponent.createRatingLabel(panel, downloads, AllIcons.Plugins.Downloads);
      }
    }
    else {
      String version = !myPlugin.isBundled() || myPlugin.allowBundledUpdate() ? myPlugin.getVersion() : "bundled";
      if (!StringUtil.isEmptyOrSpaces(version)) {
        myVersion = GridCellPluginComponent.createRatingLabel(panel, version, null);
      }
    }

    String vendor = myPlugin.isBundled() ? null : StringUtil.trim(myPlugin.getVendor());
    if (!StringUtil.isEmptyOrSpaces(vendor)) {
      myVendor = GridCellPluginComponent.createRatingLabel(panel, TextHorizontalLayout.FIX_LABEL, vendor, null, null, true);
    }
  }

  public void setUpdateDescriptor(@Nullable IdeaPluginDescriptor descriptor) {
    if (myUpdateDescriptor == null && descriptor == null) {
      return;
    }

    myUpdateDescriptor = descriptor;

    if (descriptor == null) {
      if (myVersion != null) {
        myVersion.setText(myPlugin.getVersion());
      }
      if (myUpdateButton != null) {
        myUpdateButton.setVisible(false);
      }
    }
    else {
      if (myVersion != null) {
        myVersion.setText(myPlugin.getVersion() + " " + UIUtil.rightArrow() + " " + descriptor.getVersion());
      }
      if (myUpdateButton == null) {
        myNameAndButtons.addButtonAsFirstComponent(myUpdateButton = new UpdateButton());
        myUpdateButton.addActionListener(e -> myPluginModel.installOrUpdatePlugin(myPlugin, myUpdateDescriptor));
      }
      else {
        myUpdateButton.setVisible(true);
      }
    }

    myNameAndButtons.doLayout();
  }

  @Override
  public void setListeners(@NotNull LinkListener<? super IdeaPluginDescriptor> listener,
                           @NotNull LinkListener<String> searchListener,
                           @NotNull EventHandler eventHandler) {
    eventHandler.addAll(this);
    myNameAndButtons.setListeners(eventHandler);
    myEventHandler = eventHandler;
  }

  @Override
  protected void updateColors(@NotNull Color grayedFg, @NotNull Color background) {
    setBackground(background);

    Color nameForeground = null;
    Color otherForeground = grayedFg;
    boolean calcColor = true;

    if (mySelection != EventHandler.SelectionType.NONE) {
      Color color = UIManager.getColor("Plugins.selectionForeground");
      if (color != null) {
        nameForeground = otherForeground = color;
        calcColor = false;
      }
    }
    if (calcColor && !myMarketplace) {
      boolean enabled = !myUninstalled && (MyPluginModel.isInstallingOrUpdate(myPlugin) || myPluginModel.isEnabled(myPlugin));
      if (!enabled) {
        nameForeground = otherForeground = ListPluginComponent.DisabledColor;
      }
    }

    myNameComponent.setForeground(nameForeground);

    if (myRating != null) {
      myRating.setForeground(otherForeground);
    }
    if (myDownloads != null) {
      myDownloads.setForeground(otherForeground);
    }
    if (myVersion != null) {
      myVersion.setForeground(otherForeground);
    }
    if (myVendor != null) {
      myVendor.setForeground(otherForeground);
    }
  }

  @Override
  public void updateErrors() {
    boolean errors = myPluginModel.hasErrors(myPlugin);
    updateIcon(errors, myUninstalled || !myPluginModel.isEnabled(myPlugin));

    if (errors) {
      boolean addListeners = myErrorComponent == null && myEventHandler != null;

      if (myErrorPanel == null) {
        myErrorPanel = new NonOpaquePanel();
        myCenterPanel.add(myErrorPanel, VerticalLayout.FILL_HORIZONTAL);
      }

      Ref<String> enableAction = new Ref<>();
      String message = myPluginModel.getErrorMessage(myPlugin, enableAction);
      myErrorComponent = ErrorComponent.show(myErrorPanel, BorderLayout.CENTER, myErrorComponent, message, enableAction.get(),
                                             enableAction.isNull() ? null : () -> myPluginModel.enableRequiredPlugins(myPlugin));
      myErrorComponent.setBorder(JBUI.Borders.emptyTop(5));

      if (addListeners) {
        myEventHandler.add(myErrorPanel);
        myEventHandler.add(myErrorComponent);
      }
    }
    else if (myErrorPanel != null) {
      myCenterPanel.remove(myErrorPanel);
      myErrorPanel = null;
      myErrorComponent = null;
    }
  }

  @Override
  protected void updateIcon(boolean errors, boolean disabled) {
    myIconComponent.setIcon(PluginLogo.getIcon(myPlugin, false, PluginManagerConfigurableNew.isJBPlugin(myPlugin), errors, disabled));
  }

  @Override
  public void showProgress() {
    showProgress(true);
  }

  private void showProgress(boolean repaint) {
    myIndicator = new OneLineProgressIndicator(false);
    myIndicator.setCancelRunnable(() -> myPluginModel.finishInstall(myPlugin, false, false));
    myNameAndButtons.setProgressComponent(this, myIndicator.createBaselineWrapper());

    MyPluginModel.addProgress(myPlugin, myIndicator);

    if (repaint) {
      fullRepaint();
    }
  }

  @Override
  public void hideProgress(boolean success) {
    myIndicator = null;
    myNameAndButtons.removeProgressComponent();

    if (success) {
      enableRestart();
    }

    fullRepaint();
  }

  @Override
  public void clearProgress() {
    myIndicator = null;
  }

  @Override
  public void enableRestart() {
    if (myInstallButton != null) {
      myNameAndButtons.removeButtonComponent(myInstallButton);
      myInstallButton = null;
    }
    if (myUpdateButton != null) {
      myNameAndButtons.removeButtonComponent(myUpdateButton);
      myUpdateButton = null;
    }
    if (myEnableDisableButton != null) {
      myNameAndButtons.removeButtonComponent(myEnableDisableButton);
      myEnableDisableButton = null;
    }
    if (myRestartButton == null) {
      myNameAndButtons.addButtonComponent(myRestartButton = new RestartButton(myPluginModel));
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

  @Override
  public void updateAfterUninstall() {
    myUninstalled = true;
    updateColors(mySelection);
    enableRestart();
  }

  public void updatePlugin() {
    if (!myMarketplace && myUpdateButton != null && myUpdateButton.isVisible()) {
      myUpdateButton.doClick();
    }
  }

  private boolean isEnabledState() {
    return myPluginModel.isEnabled(myPlugin);
  }

  @Override
  public boolean isMarketplace() {
    return myMarketplace;
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
  public void createPopupMenu(@NotNull DefaultActionGroup group, @NotNull List<? extends CellPluginComponent> selection) {
    for (CellPluginComponent component : selection) {
      if (MyPluginModel.isInstallingOrUpdate(component.myPlugin)) {
        return;
      }
    }

    boolean restart = true;
    for (CellPluginComponent component : selection) {
      if (((NewListPluginComponent)component).myRestartButton == null) {
        restart = false;
        break;
      }
    }
    if (restart) {
      group.add(new ListPluginComponent.ButtonAnAction(((NewListPluginComponent)selection.get(0)).myRestartButton));
      return;
    }

    int size = selection.size();

    if (myMarketplace) {
      JButton[] installButtons = new JButton[size];

      for (int i = 0; i < size; i++) {
        JButton button = ((NewListPluginComponent)selection.get(i)).myInstallButton;
        if (button == null || !button.isVisible() || !button.isEnabled()) {
          return;
        }
        installButtons[i] = button;
      }

      group.add(new ListPluginComponent.ButtonAnAction(installButtons));
      return;
    }

    JButton[] updateButtons = new JButton[size];

    for (int i = 0; i < size; i++) {
      JButton button = ((NewListPluginComponent)selection.get(i)).myUpdateButton;
      if (button == null || !button.isVisible()) {
        updateButtons = null;
        break;
      }
      updateButtons[i] = button;
    }

    if (updateButtons != null) {
      group.add(new ListPluginComponent.ButtonAnAction(updateButtons));
      if (size > 1) {
        return;
      }
    }

    Pair<Boolean, IdeaPluginDescriptor[]> result = getSelectionNewState(selection);
    group.add(new ListPluginComponent.MyAnAction(result.first ? "Enable" : "Disable", null, KeyEvent.VK_SPACE) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        myPluginModel.changeEnableDisable(result.second, result.first);
      }
    });

    for (CellPluginComponent component : selection) {
      if (((NewListPluginComponent)component).myUninstalled || component.myPlugin.isBundled()) {
        return;
      }
    }

    group.addSeparator();
    group.add(new ListPluginComponent.MyAnAction("Uninstall", IdeActions.ACTION_EDITOR_DELETE, EventHandler.DELETE_CODE) {
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
      if (((NewListPluginComponent)component).myRestartButton == null) {
        restart = false;
        break;
      }
    }

    if (myMarketplace) {
      if (keyCode == KeyEvent.VK_ENTER) {
        if (restart) {
          ((NewListPluginComponent)selection.get(0)).myRestartButton.doClick();
        }

        for (CellPluginComponent component : selection) {
          JButton button = ((NewListPluginComponent)component).myInstallButton;
          if (button == null || !button.isVisible() || !button.isEnabled()) {
            return;
          }
        }
        for (CellPluginComponent component : selection) {
          ((NewListPluginComponent)component).myInstallButton.doClick();
        }
      }
      return;
    }

    boolean update = true;
    for (CellPluginComponent component : selection) {
      JButton button = ((NewListPluginComponent)component).myUpdateButton;
      if (button == null || !button.isVisible()) {
        update = false;
        break;
      }
    }

    if (keyCode == KeyEvent.VK_ENTER) {
      if (restart) {
        ((NewListPluginComponent)selection.get(0)).myRestartButton.doClick();
      }
      else if (update) {
        for (CellPluginComponent component : selection) {
          ((NewListPluginComponent)component).myUpdateButton.doClick();
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
          if (((NewListPluginComponent)component).myUninstalled || component.myPlugin.isBundled()) {
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
    boolean state = ((NewListPluginComponent)selection.get(0)).isEnabledState();
    boolean setTrue = false;

    for (ListIterator<? extends CellPluginComponent> I = selection.listIterator(1); I.hasNext(); ) {
      if (state != ((NewListPluginComponent)I.next()).isEnabledState()) {
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
}