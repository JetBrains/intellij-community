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
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ButtonUI;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
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
  private final BaselineLayout myLayout = new BaselineLayout();
  private JButton myRestartButton;
  private JButton myInstallButton;
  private JButton myUpdateButton;
  private JCheckBox myEnableDisableButton;
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
    setLayout(myLayout);

    myIconComponent.setVerticalAlignment(SwingConstants.TOP);
    myIconComponent.setOpaque(false);
    myLayout.setIconComponent(myIconComponent);

    myNameComponent.setText(myPlugin.getName());
    myLayout.setNameComponent(RelativeFont.BOLD.install(myNameComponent));

    createButtons();
    createMetricsPanel();

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
        myLayout.addButtonComponent(myRestartButton = new RestartButton(myPluginModel));
      }
      else {
        myLayout.addButtonComponent(myInstallButton = new InstallButton(false));

        myInstallButton.addActionListener(e -> myPluginModel.installOrUpdatePlugin(myPlugin, null));
        myInstallButton.setEnabled(PluginManager.getPlugin(myPlugin.getPluginId()) == null);
        ColorButton.setWidth72(myInstallButton);
      }
    }
    else if (myPlugin instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)myPlugin).isDeleted()) {
      myLayout.addButtonComponent(myRestartButton = new RestartButton(myPluginModel));

      myUninstalled = true;
    }
    else {
      InstalledPluginsState pluginsState = InstalledPluginsState.getInstance();
      PluginId id = myPlugin.getPluginId();

      if (pluginsState.wasInstalled(id) || pluginsState.wasUpdated(id)) {
        myLayout.addButtonComponent(myRestartButton = new RestartButton(myPluginModel));
      }
      else {
        myLayout.addButtonComponent(myEnableDisableButton = new JCheckBox() {
          int myBaseline = -1;

          @Override
          public int getBaseline(int width, int height) {
            if (myBaseline == -1) {
              JCheckBox checkBox = new JCheckBox("Foo", true);
              Dimension size = checkBox.getPreferredSize();
              myBaseline = checkBox.getBaseline(size.width, size.height) - JBUIScale.scale(1);
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
            int scale = JBUIScale.scale(2);
            return new Dimension(size.width + scale, size.height + scale);
          }
        });

        myEnableDisableButton.setOpaque(false);
        myEnableDisableButton.setSelected(isEnabledState());
        myEnableDisableButton.addActionListener(e -> myPluginModel.changeEnableDisable(myPlugin));
      }
    }
  }

  private void createMetricsPanel() {
    JPanel panel = new NonOpaquePanel(new TextHorizontalLayout(JBUIScale.scale(7)));
    panel.setBorder(JBUI.Borders.emptyTop(5));
    myLayout.addLineComponent(panel);

    if (myMarketplace) {
      String downloads = PluginManagerConfigurableNew.getDownloads(myPlugin);
      if (downloads != null) {
        myDownloads = GridCellPluginComponent.createRatingLabel(panel, downloads, AllIcons.Plugins.Downloads);
      }

      String rating = PluginManagerConfigurableNew.getRating(myPlugin);
      if (rating != null) {
        myRating = GridCellPluginComponent.createRatingLabel(panel, rating, AllIcons.Plugins.Rating);
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
        myLayout.addButtonComponent(myUpdateButton = new UpdateButton(), 0);
        myUpdateButton.addActionListener(e -> myPluginModel.installOrUpdatePlugin(myPlugin, myUpdateDescriptor));
      }
      else {
        myUpdateButton.setVisible(true);
      }
    }

    doLayout();
  }

  @Override
  public void setListeners(@NotNull LinkListener<? super IdeaPluginDescriptor> listener,
                           @NotNull LinkListener<String> searchListener,
                           @NotNull EventHandler eventHandler) {
    myEventHandler = eventHandler;
    eventHandler.addAll(this);
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

    if (myUpdateButton != null) {
      myUpdateButton.setVisible(myUpdateDescriptor != null && !errors);
    }
    if (myEnableDisableButton != null) {
      myEnableDisableButton.setVisible(!errors);
    }

    if (errors) {
      boolean addListeners = myErrorComponent == null && myEventHandler != null;

      if (myErrorPanel == null) {
        myErrorPanel = new NonOpaquePanel();
        myLayout.addLineComponent(myErrorPanel);
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
      myLayout.removeLineComponent(myErrorPanel);
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
    myLayout.setProgressComponent(myIndicator.createBaselineWrapper());

    MyPluginModel.addProgress(myPlugin, myIndicator);

    if (repaint) {
      fullRepaint();
    }
  }

  @Override
  public void hideProgress(boolean success) {
    myIndicator = null;
    myLayout.removeProgressComponent();

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
      myLayout.removeButtonComponent(myInstallButton);
      myInstallButton = null;
    }
    if (myUpdateButton != null) {
      myLayout.removeButtonComponent(myUpdateButton);
      myUpdateButton = null;
    }
    if (myEnableDisableButton != null) {
      myLayout.removeButtonComponent(myEnableDisableButton);
      myEnableDisableButton = null;
    }
    if (myRestartButton == null) {
      myLayout.addButtonComponent(myRestartButton = new RestartButton(myPluginModel));
    }
  }

  @Override
  public void updateEnabledState() {
    if (!myUninstalled && myEnableDisableButton != null) {
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

    boolean showUpdateAndState = true;
    for (CellPluginComponent component : selection) {
      if (myPluginModel.hasErrors(component.myPlugin)) {
        showUpdateAndState = false;
        break;
      }
    }

    if (showUpdateAndState) {
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
    }

    for (CellPluginComponent component : selection) {
      if (((NewListPluginComponent)component).myUninstalled || component.myPlugin.isBundled()) {
        return;
      }
    }

    if (group.getChildrenCount() > 0) {
      group.addSeparator();
    }

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
        for (CellPluginComponent component : selection) {
          if (myPluginModel.hasErrors(component.myPlugin)) {
            return;
          }
        }

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

  private class BaselineLayout extends AbstractLayoutManager {
    private final JBValue myHGap = new JBValue.Float(10);
    private final JBValue myHOffset = new JBValue.Float(8);
    private final JBValue myButtonOffset = new JBValue.Float(6);

    private JComponent myIconComponent;
    private JLabel myNameComponent;
    private JComponent myProgressComponent;
    private final List<JComponent> myButtonComponents = new ArrayList<>();
    private final List<JComponent> myLineComponents = new ArrayList<>();
    private boolean[] myButtonEnableStates;

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      Dimension result = new Dimension(myNameComponent.getPreferredSize());

      if (myProgressComponent == null) {
        int count = myButtonComponents.size();
        if (count > 0) {
          int visibleCount = 0;

          for (Component component : myButtonComponents) {
            if (component.isVisible()) {
              Dimension size = component.getPreferredSize();
              result.width += size.width;
              result.height = Math.max(result.height, size.height);
              visibleCount++;
            }
          }

          if (visibleCount > 0) {
            result.width += myHOffset.get();
            result.width += (visibleCount - 1) * myButtonOffset.get();
          }
        }
      }
      else {
        Dimension size = myProgressComponent.getPreferredSize();
        result.width += myHOffset.get() + size.width;
        result.height = Math.max(result.height, size.height);
      }

      for (JComponent component : myLineComponents) {
        Dimension size = component.getPreferredSize();
        result.width = Math.max(result.width, size.width);
        result.height += size.height;
      }

      Dimension iconSize = myIconComponent.getPreferredSize();
      result.width += iconSize.width + myHGap.get();
      result.height = Math.max(result.height, iconSize.height);

      JBInsets.addTo(result, getInsets());
      return result;
    }

    @Override
    public void layoutContainer(Container parent) {
      Insets insets = getInsets();
      int x = insets.left;
      int y = insets.top;

      Dimension iconSize = myIconComponent.getPreferredSize();
      myIconComponent.setBounds(x, y, iconSize.width, iconSize.height);
      x += iconSize.width + myHGap.get();
      y += JBUIScale.scale(2);

      int calcNameWidth = calculateNameWidth();
      Dimension nameSize = myNameComponent.getPreferredSize();
      int baseline = y + myNameComponent.getBaseline(nameSize.width, nameSize.height);

      myNameComponent.setToolTipText(calcNameWidth < nameSize.width ? myNameComponent.getText() : null);
      nameSize.width = Math.min(nameSize.width, calcNameWidth);
      myNameComponent.setBounds(x, y, nameSize.width, nameSize.height);
      y += nameSize.height;

      int width = getWidth();

      if (myProgressComponent == null) {
        int lastX = width - insets.right;

        for (int i = myButtonComponents.size() - 1; i >= 0; i--) {
          Component component = myButtonComponents.get(i);
          if (!component.isVisible()) {
            continue;
          }
          Dimension size = component.getPreferredSize();
          lastX -= size.width;
          setBaselineBounds(lastX, baseline, component, size);
          lastX -= myButtonOffset.get();
        }
      }
      else {
        Dimension size = myProgressComponent.getPreferredSize();
        setBaselineBounds(width - size.width - insets.right, baseline, myProgressComponent, size);
      }

      int lineWidth = width - x - insets.right;

      for (JComponent component : myLineComponents) {
        int lineHeight = component.getPreferredSize().height;
        component.setBounds(x, y, lineWidth, lineHeight);
        y += lineHeight;
      }
    }

    private int calculateNameWidth() {
      Insets insets = getInsets();
      int width = getWidth() - insets.left - insets.right - myIconComponent.getPreferredSize().width - myHGap.get();

      if (myProgressComponent != null) {
        return width - myProgressComponent.getPreferredSize().width - myHOffset.get();
      }

      int visibleCount = 0;
      for (Component component : myButtonComponents) {
        if (component.isVisible()) {
          width -= component.getPreferredSize().width;
          visibleCount++;
        }
      }
      width -= myButtonOffset.get() * (visibleCount - 1);
      if (visibleCount > 0) {
        width -= myHOffset.get();
      }

      return width;
    }

    private void setBaselineBounds(int x, int y, @NotNull Component component, @NotNull Dimension size) {
      component.setBounds(x, y - component.getBaseline(size.width, size.height), size.width, size.height);
    }

    public void setIconComponent(@NotNull JComponent iconComponent) {
      assert myIconComponent == null;
      myIconComponent = iconComponent;
      add(iconComponent);
    }

    public void setNameComponent(@NotNull JLabel nameComponent) {
      assert myNameComponent == null;
      add(myNameComponent = nameComponent);
    }

    public void addLineComponent(@NotNull JComponent component) {
      myLineComponents.add(component);
      add(component);
    }

    public void removeLineComponent(@NotNull JComponent component) {
      myLineComponents.remove(component);
      remove(component);
    }

    public void addButtonComponent(@NotNull JComponent component) {
      addButtonComponent(component, -1);
    }

    public void addButtonComponent(@NotNull JComponent component, int index) {
      if (myButtonComponents.isEmpty() || index == -1) {
        myButtonComponents.add(component);
      }
      else {
        myButtonComponents.add(index, component);
      }
      add(component);
      updateVisibleOther();
    }

    public void removeButtonComponent(@NotNull JComponent component) {
      myButtonComponents.remove(component);
      remove(component);
      updateVisibleOther();
    }

    public void setProgressComponent(@NotNull JComponent progressComponent) {
      assert myProgressComponent == null;
      myProgressComponent = progressComponent;
      add(progressComponent);

      if (myEventHandler != null) {
        myEventHandler.addAll(progressComponent);
        myEventHandler.updateHover(NewListPluginComponent.this);
      }

      setVisibleOther(false);
      doLayout();
    }

    public void removeProgressComponent() {
      assert myProgressComponent != null;
      remove(myProgressComponent);
      myProgressComponent = null;

      setVisibleOther(true);
      doLayout();
    }

    private void updateVisibleOther() {
      if (myProgressComponent != null) {
        myButtonEnableStates = null;
        setVisibleOther(false);
      }
    }

    private void setVisibleOther(boolean value) {
      if (myButtonComponents.isEmpty()) {
        return;
      }
      if (value) {
        assert myButtonEnableStates != null && myButtonEnableStates.length == myButtonComponents.size();

        for (int i = 0, size = myButtonComponents.size(); i < size; i++) {
          myButtonComponents.get(i).setVisible(myButtonEnableStates[i]);
        }
        myButtonEnableStates = null;
      }
      else {
        assert myButtonEnableStates == null;
        myButtonEnableStates = new boolean[myButtonComponents.size()];

        for (int i = 0, size = myButtonComponents.size(); i < size; i++) {
          Component component = myButtonComponents.get(i);
          myButtonEnableStates[i] = component.isVisible();
          component.setVisible(false);
        }
      }
    }
  }
}