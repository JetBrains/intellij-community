// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.RelativeFont;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Component;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.intellij.ide.plugins.newui.PluginsViewCustomizerKt.getPluginsViewCustomizer;

@ApiStatus.Internal
public final class PluginManagerConfigurable
  implements SearchableConfigurable, Configurable.NoScroll, Configurable.NoMargin, Configurable.TopComponentProvider {

  public static final String ID = "preferences.pluginManager";
  public static final String SELECTION_TAB_KEY = "PluginConfigurable.selectionTab";
  public static final DataKey<Consumer<PluginInstallCallbackData>> PLUGIN_INSTALL_CALLBACK_DATA_KEY =
    DataKey.create("PLUGIN_INSTALL_CALLBACK_DATA_KEY");

  @SuppressWarnings("UseJBColor") public static final Color MAIN_BG_COLOR =
    JBColor.namedColor("Plugins.background", JBColor.lazy(() -> JBColor.isBright() ? UIUtil.getListBackground() : new Color(0x313335)));
  public static final Color SEARCH_BG_COLOR = JBColor.namedColor("Plugins.SearchField.background", MAIN_BG_COLOR);
  public static final Color SEARCH_FIELD_BORDER_COLOR = JBColor.namedColor("Plugins.borderColor", JBColor.border());

  public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy");

  private static final Logger LOG = Logger.getInstance(PluginManagerConfigurable.class);

  private PluginManagerConfigurablePanel myPanel;

  /**
   * @deprecated Use {@link PluginManagerConfigurable#PluginManagerConfigurable()}
   */
  @Deprecated
  public PluginManagerConfigurable(@Nullable Project project) {
    this();
  }

  public PluginManagerConfigurable() {
  }

  @Override
  public @NotNull String getId() {
    return ID;
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("title.plugins");
  }

  @Override
  public @NotNull String getHelpTopic() {
    return ID;
  }

  @Override
  public @NotNull JComponent getCenterComponent(@NotNull TopComponentController controller) {
    PluginManagerConfigurablePanel panel = createPanelIfNeeded();
    return panel.getCenterComponent(controller);
  }

  public @NotNull JComponent getTopComponent() {
    return getCenterComponent(TopComponentController.EMPTY);
  }

  @Override
  public @NotNull JComponent createComponent() {
    PluginManagerConfigurablePanel panel = createPanelIfNeeded();

    try {
      getPluginsViewCustomizer().processConfigurable(this);
    }
    catch (Exception e) {
      LOG.error("Error while processing configurable", e);
    }

    return panel.getComponent();
  }

  private @NotNull PluginManagerConfigurablePanel createPanelIfNeeded() {
    return createPanelIfNeeded(null);
  }

  private @NotNull PluginManagerConfigurablePanel createPanelIfNeeded(@Nullable String searchQuery) {
    if (myPanel == null) {
      myPanel = new PluginManagerConfigurablePanel();
      myPanel.init(searchQuery);
    }
    return myPanel;
  }

  public static <T extends Component> @NotNull T setTinyFont(@NotNull T component) {
    return SystemInfo.isMac ? RelativeFont.TINY.install(component) : component;
  }

  @Messages.YesNoResult
  public static int showRestartDialog() {
    return showRestartDialog(getUpdatesDialogTitle());
  }

  @Messages.YesNoResult
  public static int showRestartDialog(@NotNull @NlsContexts.DialogTitle String title) {
    return showRestartDialog(title, PluginManagerConfigurable::getUpdatesDialogMessage);
  }

  @Messages.YesNoResult
  public static int showRestartDialog(@NotNull @NlsContexts.DialogTitle String title,
                                      @NotNull Function<? super String, @Nls String> message) {
    String action = IdeBundle.message(ApplicationManager.getApplication().isRestartCapable() ?
                                      "ide.restart.action" :
                                      "ide.shutdown.action");
    return Messages.showYesNoDialog(message.apply(action),
                                    title,
                                    action,
                                    IdeBundle.message("ide.notnow.action"),
                                    Messages.getQuestionIcon());
  }

  public static void shutdownOrRestartApp() {
    shutdownOrRestartApp(getUpdatesDialogTitle());
  }

  public static void shutdownOrRestartApp(@NotNull @NlsContexts.DialogTitle String title) {
    shutdownOrRestartAppAfterInstall(title, PluginManagerConfigurable::getUpdatesDialogMessage);
  }

  static void shutdownOrRestartAppAfterInstall(@NotNull @NlsContexts.DialogTitle String title,
                                               @NotNull Function<? super String, @Nls String> message) {
    if (showRestartDialog(title, message) == Messages.YES) {
      // TODO this function should
      //  - schedule restart in invokeLater with ModalityState.nonModal();
      //  - close settings dialog.
      //  What happens:
      //  - the settings dialog should be displayed in a service coroutine.
      //  - restart awaits completion of all service coroutines.
      //  - calling restart synchronously from this function prevents completion of the service coroutine.
      //  => deadlock IDEA-335883.
      //  IDEA-335883 is currently fixed by showing the dialog outside of the container scope.
      ApplicationManagerEx.getApplicationEx().restart(true);
    }
  }

  static @NotNull @NlsContexts.DialogTitle String getUpdatesDialogTitle() {
    return IdeBundle.message("updates.dialog.title",
                             ApplicationNamesInfo.getInstance().getFullProductName());
  }

  static @NotNull @NlsContexts.DialogMessage String getUpdatesDialogMessage(@Nls @NotNull String action) {
    return IdeBundle.message("ide.restart.required.message",
                             action,
                             ApplicationNamesInfo.getInstance().getFullProductName());
  }

  /**
   * @deprecated Please use {@link #showPluginConfigurable(Project, Collection)}.
   */
  @Deprecated(since = "2020.2", forRemoval = true)
  public static void showPluginConfigurable(@Nullable Project project, IdeaPluginDescriptor @NotNull ... descriptors) {
    showPluginConfigurable(project,
                           ContainerUtil.map(descriptors, IdeaPluginDescriptor::getPluginId));
  }

  public static void showPluginConfigurable(@Nullable Project project,
                                            @NotNull Collection<PluginId> pluginIds) {
    PluginManagerConfigurable configurable = new PluginManagerConfigurable();
    ShowSettingsUtil.getInstance().editConfigurable(project,
                                                    configurable,
                                                    () -> configurable.select(pluginIds));
  }

  @ApiStatus.Internal
  public static void showSuggestedPlugins(@Nullable Project project, @Nullable FUSEventSource source) {
    PluginManagerConfigurable configurable = new PluginManagerConfigurable();
    ShowSettingsUtil.getInstance().editConfigurable(project,
                                                    configurable,
                                                    () -> {
                                                      configurable.setInstallSource(source);
                                                      configurable.openMarketplaceTab("/suggested");
                                                    });
  }

  public static void showPluginConfigurable(@Nullable Component parent,
                                            @Nullable Project project,
                                            @NotNull Collection<PluginId> pluginIds) {
    if (parent != null) {
      PluginManagerConfigurable configurable = new PluginManagerConfigurable();
      ShowSettingsUtil.getInstance().editConfigurable(parent,
                                                      configurable,
                                                      () -> configurable.select(pluginIds));
    }
    else {
      showPluginConfigurable(project, pluginIds);
    }
  }

  public static void showPluginConfigurableAndEnable(@Nullable Project project,
                                                     @NotNull Set<? extends IdeaPluginDescriptor> descriptors) {
    PluginManagerConfigurable configurable = new PluginManagerConfigurable();
    ShowSettingsUtil.getInstance().editConfigurable(project,
                                                    configurable,
                                                    () -> {
                                                      configurable.selectAndEnable(descriptors);
                                                    });
  }

  @Override
  public void disposeUIResources() {

    if (myPanel != null) {
      Disposer.dispose(myPanel);
      myPanel = null;
    }
  }

  @Override
  public void cancel() {
    if (myPanel != null) {
      myPanel.cancel();
    }
  }

  @Override
  public boolean isModified() {
    return myPanel != null && myPanel.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myPanel != null) {
      myPanel.apply();
    }
  }

  public void scheduleApply() {
    if (myPanel != null) {
      myPanel.scheduleApply();
    }
  }

  @Override
  public void reset() {
    if (myPanel != null) {
      myPanel.reset();
    }
  }

  @Override
  public @Nullable Runnable enableSearch(String option) {
    return createPanelIfNeeded(option).enableSearch(option);
  }

  public @Nullable Runnable enableSearch(String option, boolean ignoreTagMarketplaceTab) {
    return createPanelIfNeeded(option).enableSearch(option, ignoreTagMarketplaceTab);
  }

  public boolean isMarketplaceTabShowing() {
    return myPanel != null && myPanel.isMarketplaceTabShowing();
  }

  public boolean isInstalledTabShowing() {
    return myPanel != null && myPanel.isInstalledTabShowing();
  }

  public void openMarketplaceTab(@NotNull String option) {
    createPanelIfNeeded(option).openMarketplaceTab(option);
  }

  public void openInstalledTab(@NotNull String option) {
    createPanelIfNeeded(option).openInstalledTab(option);
  }

  private void setInstallSource(@Nullable FUSEventSource source) {
    createPanelIfNeeded().setInstallSource(source);
  }

  private void selectAndEnable(@NotNull Set<? extends IdeaPluginDescriptor> descriptors) {
    createPanelIfNeeded().selectAndEnable(descriptors);
  }

  private void select(@NotNull Collection<PluginId> pluginIds) {
    createPanelIfNeeded().select(pluginIds);
  }
}
