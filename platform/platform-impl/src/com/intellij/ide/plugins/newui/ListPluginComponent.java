// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.LicensingFacade;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Nls;
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
public class ListPluginComponent extends JPanel {
  public static final Color DisabledColor = JBColor.namedColor("Plugins.disabledForeground", new JBColor(0xB1B1B1, 0x696969));
  public static final Color GRAY_COLOR = JBColor.namedColor("Label.infoForeground", new JBColor(Gray._120, Gray._135));
  private static final Color HOVER_COLOR = JBColor.namedColor("Plugins.lightSelectionBackground", new JBColor(0xF5F9FF, 0x36393B));

  private final MyPluginModel myPluginModel;
  private final LinkListener<Object> mySearchListener;
  private final boolean myMarketplace;
  public IdeaPluginDescriptor myPlugin;
  private boolean myUninstalled;
  private boolean myOnlyUpdateMode;
  public IdeaPluginDescriptor myUpdateDescriptor;

  private final JLabel myNameComponent = new JLabel();
  private final JLabel myIconComponent = new JLabel(AllIcons.Plugins.PluginLogo_40);
  private final BaselineLayout myLayout = new BaselineLayout();
  protected JButton myRestartButton;
  protected InstallButton myInstallButton;
  protected JButton myUpdateButton;
  private JCheckBox myEnableDisableButton;
  private JComponent myAlignButton;
  private JLabel myRating;
  private JLabel myDownloads;
  private JLabel myVersion;
  private JLabel myVendor;
  private LicensePanel myLicensePanel;
  private LicensePanel myUpdateLicensePanel;
  private JPanel myErrorPanel;
  private JComponent myErrorComponent;
  private OneLineProgressIndicator myIndicator;
  private EventHandler myEventHandler;
  protected EventHandler.SelectionType mySelection = EventHandler.SelectionType.NONE;

  public static boolean HANDLE_FOCUS_ON_SELECTION = true;

  public ListPluginComponent(@NotNull MyPluginModel pluginModel,
                             @NotNull IdeaPluginDescriptor plugin,
                             @NotNull LinkListener<Object> searchListener,
                             boolean marketplace) {
    myPlugin = plugin;
    myPluginModel = pluginModel;
    mySearchListener = searchListener;
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

    createTag();
    createButtons();
    createMetricsPanel();
    createLicensePanel();

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

  public EventHandler.SelectionType getSelection() {
    return mySelection;
  }

  public void setSelection(@NotNull EventHandler.SelectionType type) {
    setSelection(type, type == EventHandler.SelectionType.SELECTION);
  }

  public void setSelection(@NotNull EventHandler.SelectionType type, boolean scrollAndFocus) {
    mySelection = type;

    if (scrollAndFocus) {
      scrollToVisible();
      if (getParent() != null && type == EventHandler.SelectionType.SELECTION && HANDLE_FOCUS_ON_SELECTION) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(this, true));
      }
    }

    updateColors(type);
    repaint();
  }

  public void scrollToVisible() {
    JComponent parent = (JComponent)getParent();
    if (parent == null) {
      return;
    }

    Rectangle bounds = getBounds();
    if (!parent.getVisibleRect().contains(bounds)) {
      parent.scrollRectToVisible(bounds);
    }
  }

  private void createButtons() {
    if (myMarketplace) {
      if (InstalledPluginsState.getInstance().wasInstalled(myPlugin.getPluginId())) {
        myLayout.addButtonComponent(myRestartButton = new RestartButton(myPluginModel));
      }
      else {
        myLayout.addButtonComponent(myInstallButton = new InstallButton(false));

        myInstallButton
          .addActionListener(
            e -> myPluginModel.installOrUpdatePlugin(this, myPlugin, null, ModalityState.stateForComponent(myInstallButton)));
        myInstallButton.setEnabled(PluginManagerCore.getPlugin(myPlugin.getPluginId()) == null,
                                   IdeBundle.message("plugin.status.installed"));
        ColorButton.setWidth72(myInstallButton);
      }
    }
    else {
      JCheckBox enableDisableButton = createEnableDisableButton();

      if (myPlugin instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)myPlugin).isDeleted()) {
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
          myLayout.addButtonComponent(myEnableDisableButton = enableDisableButton);
          myEnableDisableButton.setOpaque(false);
          myEnableDisableButton.setSelected(isEnabledState());
          myEnableDisableButton.addActionListener(e -> myPluginModel.changeEnableDisable(myPlugin));
        }
      }

      myLayout.addButtonComponent(myAlignButton = new JComponent() {
        @Override
        public Dimension getPreferredSize() {
          return enableDisableButton.getPreferredSize();
        }
      });
      myAlignButton.setOpaque(false);
    }
  }

  @NotNull
  private static JCheckBox createEnableDisableButton() {
    return new JCheckBox() {
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
        return new Dimension(size.width + JBUIScale.scale(8), size.height + JBUIScale.scale(2));
      }
    };
  }

  private void createMetricsPanel() {
    JPanel panel = new NonOpaquePanel(new TextHorizontalLayout(JBUIScale.scale(7)));
    panel.setBorder(JBUI.Borders.emptyTop(5));
    myLayout.addLineComponent(panel);

    if (myMarketplace) {
      String downloads = PluginManagerConfigurable.getDownloads(myPlugin);
      if (downloads != null) {
        myDownloads = createRatingLabel(panel, downloads, AllIcons.Plugins.Downloads);
      }

      String rating = PluginManagerConfigurable.getRating(myPlugin);
      if (rating != null) {
        myRating = createRatingLabel(panel, rating, AllIcons.Plugins.Rating);
      }
    }
    else {
      String version =
        !myPlugin.isBundled() || myPlugin.allowBundledUpdate() ? myPlugin.getVersion() : IdeBundle.message("plugin.status.bundled");

      if (!StringUtil.isEmptyOrSpaces(version)) {
        myVersion = createRatingLabel(panel, version, null);
      }
    }

    String vendor = myPlugin.isBundled() ? null : StringUtil.trim(myPlugin.getVendor());
    if (!StringUtil.isEmptyOrSpaces(vendor)) {
      myVendor = createRatingLabel(panel, TextHorizontalLayout.FIX_LABEL, vendor, null, null, true);
    }
  }

  private void createTag() {
    String tag = null;

    if (myPlugin.getProductCode() == null) {
      if (myMarketplace && !LicensePanel.isEA2Product(myPlugin.getPluginId().getIdString())) {
        List<String> tags = ((PluginNode)myPlugin).getTags();
        if (tags != null && tags.contains(Tags.Paid.name())) {
          tag = Tags.Paid.name();
        }
      }
    }
    else {
      tag = ContainerUtil.getFirstItem(PluginManagerConfigurable.getTags(myPlugin));
    }

    if (tag == null) {
      return;
    }

    TagComponent component = new TagComponent(tag);
    //noinspection unchecked
    component.setListener(mySearchListener, component);

    myLayout.setTagComponent(PluginManagerConfigurable.setTinyFont(component));
  }

  private void setTagTooltip(@Nullable String text) {
    if (myLayout.myTagComponent != null) {
      myLayout.myTagComponent.setToolTipText(text);
    }
  }

  private void createLicensePanel() {
    String productCode = myPlugin.getProductCode();
    LicensingFacade instance = LicensingFacade.getInstance();
    if (myMarketplace || productCode == null || instance == null || myPlugin.isBundled() || LicensePanel.isEA2Product(productCode)) {
      return;
    }

    LicensePanel licensePanel = new LicensePanel(true);

    String stamp = instance.getConfirmationStamp(productCode);
    if (stamp == null) {
      if (ApplicationManager.getApplication().isEAP()) {
        setTagTooltip("The license is not required for EAP version");
        return;
      }
      licensePanel.setText(IdeBundle.message("label.text.plugin.no.license"), true, false);
    }
    else {
      licensePanel.setTextFromStamp(stamp, instance.getExpirationDate(productCode));
    }
    setTagTooltip(licensePanel.getMessage());

    if (licensePanel.isNotification()) {
      licensePanel.setBorder(JBUI.Borders.emptyTop(3));
      //licensePanel.setLink("Manage licenses", () -> { XXX }, false);
      myLayout.addLineComponent(licensePanel);
      myLicensePanel = licensePanel;
    }
  }

  public void setOnlyUpdateMode(@Nullable IdeaPluginDescriptor descriptor) {
    myOnlyUpdateMode = true;

    if (myEnableDisableButton != null) {
      myLayout.removeButtonComponent(myEnableDisableButton);
      myEnableDisableButton = null;
    }

    setUpdateDescriptor(descriptor);
  }

  public void setUpdateDescriptor(@Nullable IdeaPluginDescriptor descriptor) {
    if (myUpdateDescriptor == null && descriptor == null) {
      return;
    }
    if (myIndicator != null || isRestartEnabled()) {
      return;
    }

    myUpdateDescriptor = descriptor;

    if (descriptor == null) {
      if (myVersion != null) {
        myVersion.setText(myPlugin.getVersion());
      }
      if (myUpdateLicensePanel != null) {
        myLayout.removeLineComponent(myUpdateLicensePanel);
        myUpdateLicensePanel = null;
      }
      if (myUpdateButton != null) {
        myUpdateButton.setVisible(false);
      }
      if (myAlignButton != null) {
        myAlignButton.setVisible(false);
      }
    }
    else {
      if (myVersion != null) {
        myVersion.setText(PluginManagerConfigurable.getVersion(myPlugin, descriptor));
      }
      if (myPlugin.getProductCode() == null && descriptor.getProductCode() != null &&
          !myPlugin.isBundled() && !LicensePanel.isEA2Product(descriptor.getProductCode())) {
        if (myUpdateLicensePanel == null) {
          myLayout.addLineComponent(myUpdateLicensePanel = new LicensePanel(true));
          myUpdateLicensePanel.setBorder(JBUI.Borders.emptyTop(3));
          myUpdateLicensePanel.setVisible(myErrorPanel == null);
          if (myEventHandler != null) {
            myEventHandler.addAll(myUpdateLicensePanel);
          }
        }

        myUpdateLicensePanel
          .setText(IdeBundle.message("label.next.plugin.version.is.paid.use.the.trial.for.up.to.30.days.or"), true, false);
        myUpdateLicensePanel.showBuyPlugin(() -> myUpdateDescriptor);
        myUpdateLicensePanel.setVisible(true);
      }
      if (myUpdateButton == null) {
        myLayout.addButtonComponent(myUpdateButton = new UpdateButton(), 0);
        myUpdateButton.addActionListener(
          e -> myPluginModel.installOrUpdatePlugin(this, myPlugin, myUpdateDescriptor, ModalityState.stateForComponent(myUpdateButton)));
      }
      else {
        myUpdateButton.setEnabled(true);
        myUpdateButton.setVisible(true);
      }
      if (myAlignButton != null) {
        myAlignButton.setVisible(myEnableDisableButton != null && !myEnableDisableButton.isVisible());
      }
    }

    doLayout();
  }

  public void setListeners(@NotNull EventHandler eventHandler) {
    myEventHandler = eventHandler;
    eventHandler.addAll(this);
  }

  protected void updateColors(@NotNull EventHandler.SelectionType type) {
    updateColors(GRAY_COLOR, type == EventHandler.SelectionType.NONE ? PluginManagerConfigurable.MAIN_BG_COLOR : HOVER_COLOR);
  }

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
        nameForeground = otherForeground = DisabledColor;
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

  public void updateErrors() {
    Ref<String> enableAction = new Ref<>();
    String message = myPluginModel.getErrorMessage(myPlugin, enableAction);
    boolean errors = message != null;
    updateIcon(errors, myUninstalled || !myPluginModel.isEnabled(myPlugin));

    if (myAlignButton != null) {
      myAlignButton.setVisible(myRestartButton != null);
    }

    if (errors) {
      boolean addListeners = myErrorComponent == null && myEventHandler != null;

      if (myErrorPanel == null) {
        myErrorPanel = new NonOpaquePanel();
        myLayout.addLineComponent(myErrorPanel);
      }

      myErrorComponent = ErrorComponent.show(myErrorPanel, BorderLayout.CENTER, myErrorComponent, message, enableAction.get(),
                                             enableAction.isNull() ? null : () -> myPluginModel.enableRequiredPlugins(myPlugin));
      myErrorComponent.setBorder(JBUI.Borders.emptyTop(5));

      if (addListeners) {
        myEventHandler.addAll(myErrorPanel);
      }
    }
    else if (myErrorPanel != null) {
      myLayout.removeLineComponent(myErrorPanel);
      myErrorPanel = null;
      myErrorComponent = null;
    }

    if (myLicensePanel != null) {
      myLicensePanel.setVisible(!errors);
    }
    if (myUpdateLicensePanel != null) {
      myUpdateLicensePanel.setVisible(!errors);
    }
  }

  protected void updateIcon(boolean errors, boolean disabled) {
    myIconComponent.setIcon(myPluginModel.getIcon(myPlugin, false, false, errors, disabled));
  }

  public void showProgress() {
    showProgress(true);
  }

  private void showProgress(boolean repaint) {
    myIndicator = new OneLineProgressIndicator(false);
    myIndicator.setCancelRunnable(() -> myPluginModel.finishInstall(myPlugin, null, false, false, true));
    myLayout.setProgressComponent(myIndicator.createBaselineWrapper());

    MyPluginModel.addProgress(myPlugin, myIndicator);

    if (repaint) {
      fullRepaint();
    }
  }

  public void hideProgress(boolean success, boolean restartRequired) {
    myIndicator = null;
    myLayout.removeProgressComponent();

    if (success) {
      if (restartRequired) {
        enableRestart();
      }
      else if (myInstallButton != null) {
        myInstallButton.setEnabled(false, IdeBundle.message("plugin.status.installed"));
      }
      else if (myUpdateButton != null) {
        myUpdateButton.setEnabled(false);
        myUpdateButton.setText(IdeBundle.message("plugin.status.installed"));
      }
    }

    fullRepaint();
  }

  public void clearProgress() {
    myIndicator = null;
  }

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
      myLayout.addButtonComponent(myRestartButton = new RestartButton(myPluginModel), 0);
    }
    if (myAlignButton != null) {
      myAlignButton.setVisible(true);
    }
  }

  public void updateEnabledState() {
    if (!myUninstalled && myEnableDisableButton != null) {
      myEnableDisableButton.setSelected(isEnabledState());
    }
    updateErrors();
    setSelection(mySelection, false);
  }

  public void updateAfterUninstall(boolean needRestartForUninstall) {
    myUninstalled = true;
    updateColors(mySelection);
    if (needRestartForUninstall) {
      enableRestart();
    }
  }

  public void updatePlugin() {
    if (!myMarketplace && myUpdateButton != null && myUpdateButton.isVisible() && myUpdateButton.isEnabled()) {
      myUpdateButton.doClick();
    }
  }

  protected boolean isEnabledState() {
    return myPluginModel.isEnabled(myPlugin);
  }

  public boolean isMarketplace() {
    return myMarketplace;
  }

  public boolean isRestartEnabled() {
    return myRestartButton != null && myRestartButton.isVisible();
  }

  public boolean isUpdatedWithoutRestart() {
    return myUpdateButton != null && myUpdateButton.isVisible() && !myUpdateButton.isEnabled();
  }

  public boolean underProgress() {
    return myIndicator != null;
  }

  public void close() {
    if (myIndicator != null) {
      MyPluginModel.removeProgress(myPlugin, myIndicator);
      myIndicator = null;
    }
    myPluginModel.removeComponent(this);
  }

  public void createPopupMenu(@NotNull DefaultActionGroup group, @NotNull List<? extends ListPluginComponent> selection) {
    for (ListPluginComponent component : selection) {
      if (MyPluginModel.isInstallingOrUpdate(component.myPlugin)) {
        return;
      }
    }

    boolean restart = true;
    for (ListPluginComponent component : selection) {
      if (component.myRestartButton == null) {
        restart = false;
        break;
      }
    }
    if (restart) {
      group.add(new ButtonAnAction(selection.get(0).myRestartButton));
      return;
    }

    int size = selection.size();

    if (myMarketplace) {
      JButton[] installButtons = new JButton[size];

      for (int i = 0; i < size; i++) {
        JButton button = selection.get(i).myInstallButton;
        if (button == null || !button.isVisible() || !button.isEnabled()) {
          return;
        }
        installButtons[i] = button;
      }

      group.add(new ButtonAnAction(installButtons));
      return;
    }

    JButton[] updateButtons = new JButton[size];

    for (int i = 0; i < size; i++) {
      JButton button = selection.get(i).myUpdateButton;
      if (button == null || !button.isVisible() || !button.isEnabled()) {
        updateButtons = null;
        break;
      }
      updateButtons[i] = button;
    }

    if (updateButtons != null) {
      group.add(new ButtonAnAction(updateButtons));
      if (size > 1) {
        return;
      }
    }

    if (myOnlyUpdateMode) {
      return;
    }

    Pair<Boolean, IdeaPluginDescriptor[]> result = getSelectionNewState(selection);
    group.add(new MyAnAction(
      result.first ? IdeBundle.message("plugins.configurable.enable.button") : IdeBundle.message("plugins.configurable.disable.button"),
      null, KeyEvent.VK_SPACE) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        myPluginModel.changeEnableDisable(result.second, result.first);
      }
    });

    for (ListPluginComponent component : selection) {
      if (component.myUninstalled || component.myPlugin.isBundled()) {
        return;
      }
    }

    if (group.getChildrenCount() > 0) {
      group.addSeparator();
    }

    group.add(new MyAnAction(IdeBundle.message("plugins.configurable.uninstall.button"), IdeActions.ACTION_EDITOR_DELETE,
                             EventHandler.DELETE_CODE) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (!MyPluginModel.showUninstallDialog(ListPluginComponent.this, selection)) {
          return;
        }
        for (ListPluginComponent component : selection) {
          myPluginModel.uninstallAndUpdateUi(component, component.myPlugin);
        }
      }
    });
  }

  public void handleKeyAction(int keyCode, @NotNull List<? extends ListPluginComponent> selection) {
    for (ListPluginComponent component : selection) {
      if (MyPluginModel.isInstallingOrUpdate(component.myPlugin)) {
        return;
      }
    }

    boolean restart = true;
    for (ListPluginComponent component : selection) {
      if (component.myRestartButton == null) {
        restart = false;
        break;
      }
    }

    if (myMarketplace) {
      if (keyCode == KeyEvent.VK_ENTER) {
        if (restart) {
          selection.get(0).myRestartButton.doClick();
        }

        for (ListPluginComponent component : selection) {
          JButton button = component.myInstallButton;
          if (button == null || !button.isVisible() || !button.isEnabled()) {
            return;
          }
        }
        for (ListPluginComponent component : selection) {
          component.myInstallButton.doClick();
        }
      }
      return;
    }

    boolean update = true;
    for (ListPluginComponent component : selection) {
      JButton button = component.myUpdateButton;
      if (button == null || !button.isVisible() || !button.isEnabled()) {
        update = false;
        break;
      }
    }

    if (keyCode == KeyEvent.VK_ENTER) {
      if (restart) {
        selection.get(0).myRestartButton.doClick();
      }
      else if (update) {
        for (ListPluginComponent component : selection) {
          component.myUpdateButton.doClick();
        }
      }
    }
    else if (!restart && !update) {
      if (myOnlyUpdateMode) {
        return;
      }
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
        for (ListPluginComponent component : selection) {
          if (component.myUninstalled || component.myPlugin.isBundled()) {
            return;
          }
        }
        if (!MyPluginModel.showUninstallDialog(this, selection)) {
          return;
        }
        for (ListPluginComponent component : selection) {
          myPluginModel.uninstallAndUpdateUi(this, component.myPlugin);
        }
      }
    }
  }

  protected void fullRepaint() {
    Container parent = getParent();
    parent.doLayout();
    parent.revalidate();
    parent.repaint();
  }

  @NotNull
  public IdeaPluginDescriptor getPluginDescriptor() {
    return myPlugin;
  }

  @NotNull
  private static Pair<Boolean, IdeaPluginDescriptor[]> getSelectionNewState(@NotNull List<? extends ListPluginComponent> selection) {
    boolean state = selection.get(0).isEnabledState();
    boolean setTrue = false;

    for (ListIterator<? extends ListPluginComponent> I = selection.listIterator(1); I.hasNext(); ) {
      if (state != I.next().isEnabledState()) {
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

  @NotNull
  static JLabel createRatingLabel(@NotNull JPanel panel, @NotNull String text, @Nullable Icon icon) {
    return createRatingLabel(panel, null, text, icon, null, true);
  }

  @NotNull
  static JLabel createRatingLabel(@NotNull JPanel panel,
                                  @Nullable Object constraints,
                                  @NotNull String text,
                                  @Nullable Icon icon,
                                  @Nullable Color color,
                                  boolean tiny) {
    JLabel label = new JLabel(text, icon, SwingConstants.CENTER);
    label.setOpaque(false);
    label.setIconTextGap(2);
    if (color != null) {
      label.setForeground(color);
    }
    panel.add(tiny ? PluginManagerConfigurable.setTinyFont(label) : label, constraints);
    return label;
  }

  public static class ButtonAnAction extends DumbAwareAction {
    private final JButton[] myButtons;

    ButtonAnAction(JButton @NotNull ... buttons) {
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
    MyAnAction(@Nls @Nullable String text, @Nullable String actionId, int keyCode) {
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

  private class BaselineLayout extends AbstractLayoutManager {
    private final JBValue myHGap = new JBValue.Float(10);
    private final JBValue myHOffset = new JBValue.Float(8);
    private final JBValue myButtonOffset = new JBValue.Float(6);

    private JComponent myIconComponent;
    private JLabel myNameComponent;
    private JComponent myProgressComponent;
    private JComponent myTagComponent;
    private final List<JComponent> myButtonComponents = new ArrayList<>();
    private final List<JComponent> myLineComponents = new ArrayList<>();
    private boolean[] myButtonEnableStates;

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      Dimension result = new Dimension(myNameComponent.getPreferredSize());

      if (myProgressComponent == null) {
        if (myTagComponent != null) {
          Dimension size = myTagComponent.getPreferredSize();
          result.width += size.width + 2 * myHOffset.get();
          result.height = Math.max(result.height, size.height);
        }

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
        if (component.isVisible()) {
          Dimension size = component.getPreferredSize();
          result.width = Math.max(result.width, size.width);
          result.height += size.height;
        }
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
        if (myTagComponent != null) {
          setBaselineBounds(x + nameSize.width + myHOffset.get(), baseline, myTagComponent, myTagComponent.getPreferredSize());
        }

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
        if (component.isVisible()) {
          int lineHeight = component.getPreferredSize().height;
          component.setBounds(x, y, lineWidth, lineHeight);
          y += lineHeight;
        }
      }
    }

    private int calculateNameWidth() {
      Insets insets = getInsets();
      int width = getWidth() - insets.left - insets.right - myIconComponent.getPreferredSize().width - myHGap.get();

      if (myProgressComponent != null) {
        return width - myProgressComponent.getPreferredSize().width - myHOffset.get();
      }

      if (myTagComponent != null) {
        width -= myTagComponent.getPreferredSize().width + 2 * myHOffset.get();
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

    public void setTagComponent(@NotNull JComponent component) {
      assert myTagComponent == null;
      add(myTagComponent = component);
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
        myEventHandler.updateHover(ListPluginComponent.this);
      }

      setVisibleOther(false);
      doLayout();
    }

    public void removeProgressComponent() {
      if (myProgressComponent == null) {
        return;
      }

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
      if (myTagComponent != null) {
        myTagComponent.setVisible(value);
      }

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