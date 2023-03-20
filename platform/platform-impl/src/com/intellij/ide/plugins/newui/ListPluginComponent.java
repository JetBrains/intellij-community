// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.org.PluginManagerFilters;
import com.intellij.internal.inspector.PropertyBean;
import com.intellij.internal.inspector.UiInspectorContextProvider;
import com.intellij.internal.inspector.UiInspectorUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.LicensingFacade;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ButtonUI;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ListPluginComponent extends JPanel {
  public static final Color DisabledColor = JBColor.namedColor("Plugins.disabledForeground", new JBColor(0xB1B1B1, 0x696969));
  public static final Color GRAY_COLOR = JBColor.namedColor("Label.infoForeground", new JBColor(Gray._120, Gray._135));
  public static final Color SELECTION_COLOR = JBColor.namedColor("Plugins.lightSelectionBackground", new JBColor(0xEDF6FE, 0x464A4D));
  public static final Color HOVER_COLOR = JBColor.namedColor("Plugins.hoverBackground", new JBColor(0xEDF6FE, 0x464A4D));

  private static final Ref<Boolean> HANDLE_FOCUS_ON_SELECTION = new Ref<>(Boolean.TRUE);

  private final MyPluginModel myPluginModel;
  private final LinkListener<Object> mySearchListener;
  private final boolean myMarketplace;
  private final boolean myIsAvailable;
  private @NotNull IdeaPluginDescriptor myPlugin;
  private final @NotNull PluginsGroup myGroup;
  private boolean myOnlyUpdateMode;
  private boolean myAfterUpdate;
  public IdeaPluginDescriptor myUpdateDescriptor;
  IdeaPluginDescriptor myInstalledDescriptorForMarketplace;

  private final JLabel myNameComponent = new JLabel();
  private final JLabel myIconComponent = new JLabel(AllIcons.Plugins.PluginLogo);
  private final BaselineLayout myLayout = new BaselineLayout();
  JButton myRestartButton;
  InstallButton myInstallButton;
  JButton myUpdateButton;
  private JComponent myEnableDisableButton;
  private JCheckBox myChooseUpdateButton;
  private JComponent myAlignButton;
  private JPanel myMetricsPanel;
  private JLabel myRating;
  private JLabel myDownloads;
  private JLabel myVersion;
  private JLabel myVendor;
  private LicensePanel myLicensePanel;
  private LicensePanel myUpdateLicensePanel;
  private JPanel myErrorPanel;
  private ErrorComponent myErrorComponent;
  private ProgressIndicatorEx myIndicator;
  private EventHandler myEventHandler;
  @NotNull private EventHandler.SelectionType mySelection = EventHandler.SelectionType.NONE;

  public ListPluginComponent(@NotNull MyPluginModel pluginModel,
                             @NotNull IdeaPluginDescriptor plugin,
                             @NotNull PluginsGroup group,
                             @NotNull LinkListener<Object> searchListener,
                             boolean marketplace) {
    myPlugin = plugin;
    myGroup = group;
    myPluginModel = pluginModel;
    mySearchListener = searchListener;
    myMarketplace = marketplace;
    boolean compatible = plugin instanceof PluginNode // FIXME: dependencies not available here, hard coded for now
                         ? !"com.intellij.kmm".equals(plugin.getPluginId().getIdString()) || SystemInfoRt.isMac
                         : PluginManagerCore.getIncompatiblePlatform(plugin).isEmpty();
    myIsAvailable = (compatible || isInstalledAndEnabled()) && PluginManagerFilters.getInstance().isPluginAllowed(!marketplace, plugin);
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

    if (myIsAvailable) {
      createButtons();
      createMetricsPanel();
      createLicensePanel();
    }
    else {
      createNotAvailableMarker(compatible);
    }

    if (marketplace && myInstalledDescriptorForMarketplace == null) {
      updateIcon(false, !myIsAvailable);
    }
    else {
      updateErrors();
    }
    if (MyPluginModel.isInstallingOrUpdate(myPlugin)) {
      showProgress(false);
    }
    updateColors(EventHandler.SelectionType.NONE);

    UiInspectorUtil.registerProvider(this, new PluginIdUiInspectorContextProvider());
  }

  @NotNull PluginsGroup getGroup() { return myGroup; }

  @NotNull EventHandler.SelectionType getSelection() {
    return mySelection;
  }

  void setSelection(@NotNull EventHandler.SelectionType type) {
    setSelection(type, type == EventHandler.SelectionType.SELECTION);
  }

  void setSelection(@NotNull EventHandler.SelectionType type, boolean scrollAndFocus) {
    mySelection = type;

    if (scrollAndFocus) {
      JComponent parent = (JComponent)getParent();
      if (parent != null) {
        scrollToVisible(parent, getBounds());

        if (type == EventHandler.SelectionType.SELECTION &&
            HANDLE_FOCUS_ON_SELECTION.get()) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(this, true));
        }
      }
    }

    updateColors(type);
    repaint();
  }

  void onSelection(@NotNull Runnable runnable) {
    try {
      HANDLE_FOCUS_ON_SELECTION.set(Boolean.FALSE);
      runnable.run();
    }
    finally {
      HANDLE_FOCUS_ON_SELECTION.set(Boolean.TRUE);
    }
  }

  private static void scrollToVisible(@NotNull JComponent parent,
                                      @NotNull Rectangle bounds) {
    if (!parent.getVisibleRect().contains(bounds)) {
      parent.scrollRectToVisible(bounds);
    }
  }

  private void createNotAvailableMarker(boolean compatible) {
    myInstallButton = createInstallButton();
    if (!compatible) {
      setupNotCompatibleMarkerButton();
    }
    else {
      setupNotAllowedMarkerButton();
    }
    myLayout.addButtonComponent(myInstallButton);
  }

  private void setupNotCompatibleMarkerButton() {
    myInstallButton.setButtonColors(false);
    myInstallButton.setEnabled(false, IdeBundle.message("plugins.configurable.unavailable.for.platform"));
    myInstallButton.setToolTipText(IdeBundle.message("plugins.configurable.plugin.unavailable.for.platform", SystemInfo.getOsName()));
  }

  private void setupNotAllowedMarkerButton() {
    if (myMarketplace || myPluginModel.getState(myPlugin).isDisabled()) {
      myInstallButton.setButtonColors(false);
      myInstallButton.setEnabled(false, IdeBundle.message("plugin.status.not.allowed"));
      myInstallButton.setToolTipText(IdeBundle.message("plugin.status.not.allowed.tooltip"));
    }
    else {
      myInstallButton.setButtonColors(false);
      myInstallButton.setEnabled(true, IdeBundle.message("plugin.status.not.allowed.but.enabled"));
      myInstallButton.setText(IdeBundle.message("plugin.status.not.allowed.but.enabled"));
      myInstallButton.setToolTipText(IdeBundle.message("plugin.status.not.allowed.tooltip.but.enabled"));
      myInstallButton.setBorderColor(JBColor.red);
      myInstallButton.setTextColor(JBColor.red);
      myInstallButton.addActionListener(e -> {
        myPluginModel.disable(List.of(myPlugin));
        setupNotAllowedMarkerButton();
      });
    }
    ColorButton.setWidth72(myInstallButton);
  }

  private void createButtons() {
    PluginId pluginId = myPlugin.getPluginId();
    if (myMarketplace) {
      if (InstalledPluginsState.getInstance().wasInstalled(pluginId)) {
        myLayout.addButtonComponent(myRestartButton = new RestartButton(myPluginModel));
      }
      else {
        IdeaPluginDescriptor installedDescriptorForMarketplace = PluginManagerCore.findPlugin(pluginId);

        boolean showInstall = installedDescriptorForMarketplace == null;

        myLayout.addButtonComponent(myInstallButton = createInstallButton());

        myInstallButton.addActionListener(
          e -> myPluginModel.installOrUpdatePlugin(this, myPlugin, null, ModalityState.stateForComponent(myInstallButton)));
        myInstallButton.setEnabled(showInstall, IdeBundle.message("plugin.status.installed"));

        ColorButton.setWidth72(myInstallButton);

        if (PluginDetailsPageComponent.isMultiTabs()) {
          myInstalledDescriptorForMarketplace = installedDescriptorForMarketplace;
          myInstallButton.setVisible(showInstall);

          if (NewUiUtil.isDeleted(myInstalledDescriptorForMarketplace)) {
            if (InstalledPluginsState.getInstance().wasUninstalledWithoutRestart(pluginId)) {
              myInstallButton.setVisible(true);
              myInstallButton.setEnabled(false, IdeBundle.message("plugins.configurable.uninstalled"));
              myInstallButton.setPreferredSize(null);
              myAfterUpdate = true;
            }
            else {
              myLayout.addButtonComponent(myRestartButton = new RestartButton(myPluginModel));

              myPluginModel.addUninstalled(myInstalledDescriptorForMarketplace);
            }
          }
          else {
            createEnableDisableButton(this::getInstalledDescriptorForMarketplace);
            myEnableDisableButton.setVisible(!showInstall);

            if (!showInstall) {
              updateEnabledStateUI();
            }
          }
        }
      }
    }
    else {
      if (NewUiUtil.isDeleted(myPlugin)) {
        if (InstalledPluginsState.getInstance().wasUninstalledWithoutRestart(pluginId)) {
          myLayout.addButtonComponent(myInstallButton = createInstallButton());
          myInstallButton.setVisible(true);
          myInstallButton.setEnabled(false, IdeBundle.message("plugins.configurable.uninstalled"));
          myAfterUpdate = true;
        }
        else {
          myLayout.addButtonComponent(myRestartButton = new RestartButton(myPluginModel));

          myPluginModel.addUninstalled(myPlugin);
        }
      }
      else {
        InstalledPluginsState pluginsState = InstalledPluginsState.getInstance();
        if (pluginsState.wasInstalled(pluginId) || pluginsState.wasUpdatedWithRestart(pluginId)) {
          myLayout.addButtonComponent(myRestartButton = new RestartButton(myPluginModel));
        }
        else {
          createEnableDisableButton(this::getPluginDescriptor);
          updateEnabledStateUI();
        }
      }

      myLayout.addButtonComponent(myAlignButton = new JComponent() {
        @Override
        public Dimension getPreferredSize() {
          return myEnableDisableButton instanceof JCheckBox ?
                 myEnableDisableButton.getPreferredSize() :
                 super.getPreferredSize();
        }
      });
      myAlignButton.setOpaque(false);
    }
  }

  private @NotNull InstallButton createInstallButton() {
    boolean upgradeRequired = myPlugin instanceof PluginNode && ((PluginNode)myPlugin).getSuggestedCommercialIde() != null;
    return new InstallButton(false, upgradeRequired);
  }

  private void createEnableDisableButton(@NotNull Supplier<? extends IdeaPluginDescriptor> descriptorFunction) {
    myEnableDisableButton = createEnableDisableButton(__ -> {
      List<IdeaPluginDescriptor> descriptors = List.of(descriptorFunction.get());
      if (myPluginModel.getState(myPlugin).isDisabled()) {
        myPluginModel.enable(descriptors);
      }
      else {
        myPluginModel.disable(descriptors);
      }
    });

    myLayout.addButtonComponent(myEnableDisableButton);
    myEnableDisableButton.setOpaque(false);
  }

  private static @NotNull JCheckBox createEnableDisableButton(@NotNull ActionListener listener) {
    return new JCheckBox() {

      private int myBaseline = -1;

      {
        addActionListener(listener);
      }

      @Override
      public int getBaseline(int width, int height) {
        if (myBaseline == -1) {
          JCheckBox checkBox = new JCheckBox("Foo", true);  // NON-NLS
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
        return new Dimension(
          size.width + JBUIScale.scale(8),
          size.height + JBUIScale.scale(2)
        );
      }
    };
  }

  private void createMetricsPanel() {
    myMetricsPanel = new NonOpaquePanel(new TextHorizontalLayout(JBUIScale.scale(7)));
    myMetricsPanel.setBorder(JBUI.Borders.emptyTop(5));
    myLayout.addLineComponent(myMetricsPanel);

    if (myMarketplace) {
      assert myPlugin instanceof PluginNode;
      PluginNode pluginNode = (PluginNode)myPlugin;

      String downloads = pluginNode.getPresentableDownloads();
      if (downloads != null) {
        myDownloads = createRatingLabel(myMetricsPanel, downloads, AllIcons.Plugins.Downloads);
      }

      String rating = pluginNode.getPresentableRating();
      if (rating != null) {
        myRating = createRatingLabel(myMetricsPanel, rating, AllIcons.Plugins.Rating);
      }
      if (PluginDetailsPageComponent.isMultiTabs()) {
        String version = myInstalledDescriptorForMarketplace == null ? "" : myInstalledDescriptorForMarketplace.getVersion();
        myVersion = createRatingLabel(myMetricsPanel, version, null);
        myVersion.setVisible(!StringUtil.isEmptyOrSpaces(version));
      }
    }
    else {
      String version = myPlugin.isBundled() ? IdeBundle.message("plugin.status.bundled") : myPlugin.getVersion();

      if (!StringUtil.isEmptyOrSpaces(version)) {
        myVersion = createRatingLabel(myMetricsPanel, version, null);
      }
    }

    if (!myPlugin.isBundled()) {
      String vendor = StringUtil.defaultIfEmpty(Strings.trim(myPlugin.getVendor()), Strings.trim(myPlugin.getOrganization()));
      if (!StringUtil.isEmptyOrSpaces(vendor)) {
        myVendor = createRatingLabel(myMetricsPanel, TextHorizontalLayout.FIX_LABEL, vendor, null, null, true);
      }
    }
  }

  private void createTag() {
    List<String> tags = PluginManagerConfigurable.getTags(myPlugin);
    if (!tags.isEmpty()) {
      TagComponent tagComponent = createTagComponent(tags.get(0));
      myLayout.setTagComponent(PluginManagerConfigurable.setTinyFont(tagComponent));
    }
  }

  private @NotNull TagComponent createTagComponent(@Nls @NotNull String tag) {
    TagComponent component = new TagComponent(tag);
    //noinspection unchecked
    component.setListener(mySearchListener, component);
    return component;
  }

  private void setTagTooltip(@Nullable @Nls String text) {
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
      if (ApplicationManager.getApplication().isEAP() && !Boolean.getBoolean("eap.require.license")) {
        setTagTooltip(IdeBundle.message("label.text.plugin.eap.license.not.required"));
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

  public void setOnlyUpdateMode() {
    myOnlyUpdateMode = true;

    removeButtons(false);

    myLayout.setCheckBoxComponent(myChooseUpdateButton = new JCheckBox((String)null, true));
    myChooseUpdateButton.setOpaque(false);

    IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(myPlugin.getPluginId());
    if (descriptor != null) {
      if (myDownloads != null) {
        myMetricsPanel.remove(myDownloads);
      }
      if (myRating != null) {
        myMetricsPanel.remove(myRating);
      }
      if (myVendor != null) {
        myMetricsPanel.remove(myVendor);
      }
      if (myVersion != null) {
        myMetricsPanel.remove(myVersion);
      }

      String version = NewUiUtil.getVersion(descriptor, myPlugin);
      String size = myPlugin instanceof PluginNode ?
                    ((PluginNode)myPlugin).getPresentableSize() :
                    null;
      myVersion = createRatingLabel(myMetricsPanel,
                                    null,
                                    size != null ? version + " | " + size : version,
                                    null,
                                    null,
                                    false);
    }

    updateColors(EventHandler.SelectionType.NONE);
  }

  public JCheckBox getChooseUpdateButton() {
    return myChooseUpdateButton;
  }

  public void setUpdateDescriptor(@Nullable IdeaPluginDescriptor descriptor) {
    if (myMarketplace && myInstalledDescriptorForMarketplace == null) {
      return;
    }
    if (myUpdateDescriptor == null && descriptor == null) {
      return;
    }
    if (myIndicator != null || isRestartEnabled()) {
      return;
    }

    myUpdateDescriptor = descriptor;

    IdeaPluginDescriptor plugin = getDescriptorForActions();

    if (descriptor == null) {
      if (myVersion != null) {
        myVersion.setText(plugin.getVersion());
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
        myVersion.setText(NewUiUtil.getVersion(plugin, descriptor));
      }
      if (plugin.getProductCode() == null && descriptor.getProductCode() != null &&
          !plugin.isBundled() && !LicensePanel.isEA2Product(descriptor.getProductCode())) {
        if (myUpdateLicensePanel == null) {
          myLayout.addLineComponent(myUpdateLicensePanel = new LicensePanel(true));
          myUpdateLicensePanel.setBorder(JBUI.Borders.emptyTop(3));
          myUpdateLicensePanel.setVisible(myErrorPanel == null);
          if (myEventHandler != null) {
            myEventHandler.addAll(myUpdateLicensePanel);
          }
        }

        String message;
        if (myUpdateDescriptor instanceof PluginNode && ((PluginNode)myUpdateDescriptor).getTags().contains(Tags.Freemium.name())) {
          message = IdeBundle.message("label.next.plugin.version.is.freemium");
        }
        else {
          message = IdeBundle.message("label.next.plugin.version.is.paid.use.the.trial.for.up.to.30.days.or");
        }
        myUpdateLicensePanel.setText(message, true, false);
        myUpdateLicensePanel.showBuyPlugin(() -> myUpdateDescriptor);
        myUpdateLicensePanel.setVisible(true);
      }
      if (myUpdateButton == null) {
        myLayout.addButtonComponent(myUpdateButton = new UpdateButton(), 0);
        myUpdateButton.addActionListener(
          e -> myPluginModel.installOrUpdatePlugin(this, plugin, myUpdateDescriptor, ModalityState.stateForComponent(myUpdateButton)));
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

  void updateColors(@NotNull EventHandler.SelectionType type) {
    updateColors(GRAY_COLOR, type == EventHandler.SelectionType.NONE
                             ? PluginManagerConfigurable.MAIN_BG_COLOR
                             : (type == EventHandler.SelectionType.HOVER ? HOVER_COLOR : SELECTION_COLOR));
  }

  private void updateColors(@NotNull Color grayedFg, @NotNull Color background) {
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

    if (calcColor && !myIsAvailable) {
      calcColor = false;
      nameForeground = otherForeground = DisabledColor;
    }

    if (calcColor && (!myMarketplace || myInstalledDescriptorForMarketplace != null)) {
      IdeaPluginDescriptor plugin = getDescriptorForActions();
      boolean disabled = myPluginModel.isUninstalled(plugin) || !MyPluginModel.isInstallingOrUpdate(plugin) && !isEnabledState();
      if (disabled) {
        nameForeground = otherForeground = DisabledColor;
      }
    }

    myNameComponent.setHorizontalTextPosition(SwingConstants.LEFT);
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
    IdeaPluginDescriptor plugin = getDescriptorForActions();
    List<? extends HtmlChunk> errors = myOnlyUpdateMode ? List.of() : myPluginModel.getErrors(plugin);
    boolean hasErrors = !errors.isEmpty();
    updateIcon(hasErrors, myPluginModel.isUninstalled(plugin) || !isEnabledState() || !myIsAvailable);

    if (myAlignButton != null) {
      myAlignButton.setVisible(myRestartButton != null || myAfterUpdate);
    }

    if (hasErrors) {
      boolean addListeners = myErrorComponent == null && myEventHandler != null;

      if (myErrorPanel == null) {
        myErrorPanel = new NonOpaquePanel();
        myLayout.addLineComponent(myErrorPanel);
      }

      if (myErrorComponent == null) {
        myErrorComponent = new ErrorComponent();
        myErrorComponent.setBorder(JBUI.Borders.emptyTop(5));
        myErrorPanel.add(myErrorComponent, BorderLayout.CENTER);
      }
      myErrorComponent.setErrors(errors, () -> myPluginModel.enableRequiredPlugins(plugin));

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
      myLicensePanel.setVisible(!hasErrors);
    }
    if (myUpdateLicensePanel != null) {
      myUpdateLicensePanel.setVisible(!hasErrors);
    }
  }

  private void updateIcon(boolean errors, boolean disabled) {
    myIconComponent.setIcon(myPluginModel.getIcon(myPlugin, false, errors, disabled));
  }

  public void showProgress() {
    showProgress(true);
  }

  private void showProgress(boolean repaint) {
    if (PluginDetailsPageComponent.isMultiTabs()) {
      myIndicator = new AbstractProgressIndicatorExBase();
      myLayout.setProgressComponent(new AsyncProcessIcon("PluginListComponentIconProgress") {
        @Override
        public int getBaseline(int width, int height) {
          return (int)(height * 0.85);
        }

        @Override
        public void removeNotify() {
          super.removeNotify();
          if (!isDisposed()) {
            Disposer.dispose(this);
          }
        }
      });
    }
    else {
      OneLineProgressIndicator indicator = new OneLineProgressIndicator(false);
      indicator.setCancelRunnable(() -> myPluginModel.finishInstall(getDescriptorForActions(), null, false, false, true));
      myLayout.setProgressComponent(indicator.createBaselineWrapper());
      myIndicator = indicator;
    }

    MyPluginModel.addProgress(getDescriptorForActions(), myIndicator);

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
      else {
        if (myInstallButton != null) {
          myInstallButton.setEnabled(false, IdeBundle.message("plugin.status.installed"));
          if (PluginDetailsPageComponent.isMultiTabs() && myInstallButton.isVisible()) {
            myInstalledDescriptorForMarketplace = PluginManagerCore.findPlugin(myPlugin.getPluginId());
            if (myInstalledDescriptorForMarketplace != null) {
              myInstallButton.setVisible(false);
              myEnableDisableButton.setVisible(true);
              myVersion.setText(myInstalledDescriptorForMarketplace.getVersion());
              myVersion.setVisible(true);
              updateEnabledStateUI();
              fullRepaint();
              return;
            }
          }
        }
        if (myUpdateButton != null) {
          myUpdateButton.setEnabled(false);
          myUpdateButton.setText(IdeBundle.message("plugin.status.installed"));
          myAfterUpdate = true;
        }
        if (myEnableDisableButton != null) {
          myLayout.removeButtonComponent(myEnableDisableButton);
          myEnableDisableButton = null;

          if (myAlignButton != null) {
            myAlignButton.setVisible(true);
          }
        }
      }
    }

    fullRepaint();
  }

  public void clearProgress() {
    myIndicator = null;
  }

  public void enableRestart() {
    removeButtons(true);
  }

  private void removeButtons(boolean showRestart) {
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
    if (myIsAvailable && showRestart && myRestartButton == null) {
      myLayout.addButtonComponent(myRestartButton = new RestartButton(myPluginModel), 0);
    }
    if (myAlignButton != null) {
      myAlignButton.setVisible(true);
    }
  }

  public void updateEnabledState() {
    if (myMarketplace && myInstalledDescriptorForMarketplace == null) {
      return;
    }
    if (!myPluginModel.isUninstalled(getDescriptorForActions())) {
      updateEnabledStateUI();
    }
    updateErrors();
    setSelection(mySelection, false);
  }

  private void updateEnabledStateUI() {
    if (myEnableDisableButton instanceof JCheckBox) {
      ((JCheckBox)myEnableDisableButton).setSelected(
        myPluginModel.isEnabled(getDescriptorForActions()));
    }
    myNameComponent.setIcon(AllIcons.General.ProjectConfigurable);
  }

  public void updateAfterUninstall(boolean needRestartForUninstall) {
    myPluginModel.addUninstalled(getDescriptorForActions());
    updateColors(mySelection);
    removeButtons(needRestartForUninstall);

    if (!needRestartForUninstall &&
        InstalledPluginsState.getInstance().wasUninstalledWithoutRestart(getDescriptorForActions().getPluginId())) {
      myLayout.addButtonComponent(myInstallButton = createInstallButton());
      myInstallButton.setEnabled(false, IdeBundle.message("plugins.configurable.uninstalled"));
    }
  }

  public void updatePlugin() {
    if ((!myMarketplace || myInstalledDescriptorForMarketplace == null) &&
        myUpdateButton != null && myUpdateButton.isVisible() && myUpdateButton.isEnabled()) {
      myUpdateButton.doClick();
    }
  }

  private boolean isEnabledState() {
    return myPluginModel.isEnabled(getDescriptorForActions());
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
      MyPluginModel.removeProgress(getDescriptorForActions(), myIndicator);
      myIndicator = null;
    }
    myPluginModel.removeComponent(this);
  }

  public void createPopupMenu(@NotNull DefaultActionGroup group,
                              @NotNull List<? extends ListPluginComponent> selection) {
    if (!myIsAvailable) {
      return;
    }

    if (myOnlyUpdateMode) {
      return;
    }

    for (ListPluginComponent component : selection) {
      if (MyPluginModel.isInstallingOrUpdate(component.myPlugin) || component.myAfterUpdate) {
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
    boolean getDescriptorFunction = true;

    if (myMarketplace) {
      JButton[] installButtons = new JButton[size];
      int installCount = 0;
      int installedCount = 0;

      for (int i = 0; i < size; i++) {
        ListPluginComponent component = selection.get(i);
        JButton button = component.myInstallButton;
        if (button != null && button.isVisible() && button.isEnabled()) {
          installButtons[i] = button;
          installCount++;
        }
        else if (component.myInstalledDescriptorForMarketplace != null) {
          installedCount++;
        }
        else {
          return;
        }
      }

      if (installCount == size) {
        group.add(new ButtonAnAction(installButtons));
        return;
      }
      if (installedCount != size) {
        return;
      }

      getDescriptorFunction = false;
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

    Function<ListPluginComponent, IdeaPluginDescriptor> function =
      getDescriptorFunction ? ListPluginComponent::getPluginDescriptor : ListPluginComponent::getInstalledDescriptorForMarketplace;
    SelectionBasedPluginModelAction.addActionsTo(group,
                                                 action -> createEnableDisableAction(action, selection, function),
                                                 () -> createUninstallAction(selection, function));
  }

  public void handleKeyAction(@NotNull KeyEvent event,
                              @NotNull List<? extends ListPluginComponent> selection) {
    if (myOnlyUpdateMode) {
      if (event.getKeyCode() == KeyEvent.VK_SPACE) {
        for (ListPluginComponent component : selection) {
          component.myChooseUpdateButton.doClick();
        }
      }
      return;
    }

    for (ListPluginComponent component : selection) {
      if (MyPluginModel.isInstallingOrUpdate(component.myPlugin) || component.myAfterUpdate) {
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

    boolean getDescriptorFunction = true;

    int keyCode = event.getKeyCode();
    if (myMarketplace) {
      if (keyCode == KeyEvent.VK_ENTER) {
        if (restart) {
          selection.get(0).myRestartButton.doClick();
        }

        int installCount = 0;
        int installedCount = 0;

        for (ListPluginComponent component : selection) {
          JButton button = component.myInstallButton;
          if (button != null && button.isVisible() && button.isEnabled()) {
            installCount++;
          }
          else if (component.myInstalledDescriptorForMarketplace != null) {
            installedCount++;
          }
          else {
            return;
          }
        }
        int size = selection.size();
        if (installCount == size) {
          for (ListPluginComponent component : selection) {
            component.myInstallButton.doClick();
          }
          return;
        }
        if (installedCount != size) {
          return;
        }
        getDescriptorFunction = false;
      }
      else if (keyCode == KeyEvent.VK_SPACE || keyCode == EventHandler.DELETE_CODE) {
        int installedCount = 0;
        for (ListPluginComponent component : selection) {
          if (component.myInstalledDescriptorForMarketplace != null) {
            installedCount++;
          }
          else {
            return;
          }
        }
        if (installedCount != selection.size()) {
          return;
        }
        getDescriptorFunction = false;
      }
      else {
        return;
      }
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
      Function<ListPluginComponent, IdeaPluginDescriptor> function =
        getDescriptorFunction ? ListPluginComponent::getPluginDescriptor : ListPluginComponent::getInstalledDescriptorForMarketplace;

      DumbAwareAction action;

      if (keyCode == KeyEvent.VK_SPACE && event.getModifiersEx() == 0) {
        action = createEnableDisableAction(getEnableDisableAction(selection), selection, function);
      }
      else if (keyCode == EventHandler.DELETE_CODE) {
        action = createUninstallAction(selection, function);
      }
      else {
        return;
      }

      ActionManager.getInstance().tryToExecute(action, event, this, ActionPlaces.UNKNOWN, true);
    }
  }

  private void fullRepaint() {
    Container parent = getParent();
    parent.doLayout();
    parent.revalidate();
    parent.repaint();
  }

  public @NotNull IdeaPluginDescriptor getPluginDescriptor() {
    return myPlugin;
  }

  public IdeaPluginDescriptor getInstalledDescriptorForMarketplace() {
    return myInstalledDescriptorForMarketplace;
  }

  public IdeaPluginDescriptor getDescriptorForActions() {
    return !myMarketplace || myInstalledDescriptorForMarketplace == null ? myPlugin : myInstalledDescriptorForMarketplace;
  }

  public void setPluginDescriptor(@NotNull IdeaPluginDescriptor plugin) {
    myPlugin = plugin;
  }

  private @NotNull PluginEnableDisableAction getEnableDisableAction(@NotNull List<? extends ListPluginComponent> selection) {
    Iterator<? extends ListPluginComponent> iterator = selection.iterator();
    BooleanSupplier isGloballyEnabledGenerator = () ->
      myPluginModel.getState(iterator.next().getPluginDescriptor()) == PluginEnabledState.ENABLED;

    boolean firstDisabled = !isGloballyEnabledGenerator.getAsBoolean();
    while (iterator.hasNext()) {
      if (firstDisabled == isGloballyEnabledGenerator.getAsBoolean()) {
        return PluginEnableDisableAction.ENABLE_GLOBALLY;
      }
    }

    return PluginEnableDisableAction.globally(firstDisabled);
  }

  private @NotNull SelectionBasedPluginModelAction.EnableDisableAction<ListPluginComponent> createEnableDisableAction(@NotNull PluginEnableDisableAction action,
                                                                                                                      @NotNull List<? extends ListPluginComponent> selection,
                                                                                                                      @NotNull Function<? super ListPluginComponent, ? extends IdeaPluginDescriptor> function) {
    return new SelectionBasedPluginModelAction.EnableDisableAction<>(myPluginModel, action, true, selection, function);
  }

  private @NotNull SelectionBasedPluginModelAction.UninstallAction<ListPluginComponent> createUninstallAction(@NotNull List<? extends ListPluginComponent> selection,
                                                                                                              @NotNull Function<? super ListPluginComponent, ? extends IdeaPluginDescriptor> function) {
    return new SelectionBasedPluginModelAction.UninstallAction<>(myPluginModel, true, this, selection, function);
  }

  @NotNull
  static JLabel createRatingLabel(@NotNull JPanel panel, @NotNull @Nls String text, @Nullable Icon icon) {
    return createRatingLabel(panel, null, text, icon, null, true);
  }

  @NotNull
  static JLabel createRatingLabel(@NotNull JPanel panel,
                                  @Nullable Object constraints,
                                  @NotNull @Nls String text,
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
      super(buttons[0].getText()); //NON-NLS
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

  private class PluginIdUiInspectorContextProvider implements UiInspectorContextProvider {
    @Override
    public @NotNull List<PropertyBean> getUiInspectorContext() {
      ArrayList<PropertyBean> result = new ArrayList<>();
      result.add(new PropertyBean("Plugin ID", myPlugin.getPluginId(), true));
      result.add(new PropertyBean("Plugin Dependencies",
                                  StringUtil.join(myPlugin.getDependencies(),
                                                  it -> it.getPluginId() + (it.isOptional() ? " (optional)" : ""), ", "), true));
      return result;
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
    private JComponent myCheckBoxComponent;
    private final List<JComponent> myButtonComponents = new ArrayList<>();
    private final List<JComponent> myLineComponents = new ArrayList<>();
    private boolean[] myButtonEnableStates;

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      Dimension result = new Dimension(myNameComponent.getPreferredSize());

      if (myProgressComponent == null) {
        if (myCheckBoxComponent != null) {
          Dimension size = myCheckBoxComponent.getPreferredSize();
          result.width += size.width + myHOffset.get();
          result.height = Math.max(result.height, size.height);
        }

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

      if (myProgressComponent == null && myCheckBoxComponent != null) {
        Dimension size = myCheckBoxComponent.getPreferredSize();
        myCheckBoxComponent.setBounds(x, (parent.getHeight() - size.height) / 2, size.width, size.height);
        x += size.width + myHGap.get();
      }

      Dimension iconSize = myIconComponent.getPreferredSize();
      myIconComponent.setBounds(x, y, iconSize.width, iconSize.height);
      x += iconSize.width + myHGap.get();
      y += JBUIScale.scale(2);

      int width20 = JBUIScale.scale(20);
      int calcNameWidth = Math.max(width20, calculateNameWidth());
      Dimension nameSize = myNameComponent.getPreferredSize();
      int baseline = y + myNameComponent.getBaseline(nameSize.width, nameSize.height);

      myNameComponent.setToolTipText(calcNameWidth < nameSize.width ? myNameComponent.getText() : null);
      nameSize.width = Math.min(nameSize.width, calcNameWidth);
      myNameComponent.setBounds(x, y, nameSize.width, nameSize.height);
      y += nameSize.height;

      int width = getWidth();

      if (myProgressComponent == null) {
        int nextX = x + nameSize.width + myHOffset.get();

        if (myTagComponent != null) {
          Dimension size = myTagComponent.getPreferredSize();
          setBaselineBounds(nextX, baseline, myTagComponent, size);
          nextX += size.width;
        }

        int lastX = width - insets.right;

        if (calcNameWidth > width20) {
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
          for (JComponent component : myButtonComponents) {
            if (component.isVisible()) {
              Dimension size = component.getPreferredSize();
              setBaselineBounds(nextX, baseline, component, size);
              nextX += size.width + myButtonOffset.get();
            }
          }
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

      if (myCheckBoxComponent != null) {
        width -= myCheckBoxComponent.getPreferredSize().width + myHOffset.get();
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
      if (component instanceof ActionToolbar) {
        component.setBounds(x, getInsets().top - JBUI.scale(1), size.width, size.height);
      }
      else {
        component.setBounds(x, y - component.getBaseline(size.width, size.height), size.width, size.height);
      }
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

    public void setCheckBoxComponent(@NotNull JComponent checkBoxComponent) {
      assert myCheckBoxComponent == null;
      myCheckBoxComponent = checkBoxComponent;
      add(checkBoxComponent);
      doLayout();
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

  private boolean isInstalledAndEnabled() {
    return PluginManagerCore.getPlugin(myPlugin.getPluginId()) != null && !myPluginModel.getState(myPlugin).isDisabled();
  }
}