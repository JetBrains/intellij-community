// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class PluginManagerMain {
  private static final String TEXT_SUFFIX = "</body></html>";
  private static final String HTML_PREFIX = "<a href=\"";
  private static final String HTML_SUFFIX = "</a>";

  private PluginManagerMain() {
  }

  private static @NlsSafe String getTextPrefix() {
    int fontSize = JBUIScale.scale(12);
    int m1 = JBUIScale.scale(2);
    int m2 = JBUIScale.scale(5);
    return String.format(
           "<html><head>" +
           "    <style type=\"text/css\">" +
           "        p {" +
           "            font-family: Arial,serif; font-size: %dpt; margin: %dpx %dpx" +
           "        }" +
           "    </style>" +
           "</head><body style=\"font-family: Arial,serif; font-size: %dpt; margin: %dpx %dpx;\">",
           fontSize, m1, m1, fontSize, m2, m2);
  }

  public static boolean downloadPlugins(List<PluginNode> plugins,
                                        List<? extends IdeaPluginDescriptor> customPlugins,
                                        Runnable onSuccess,
                                        PluginEnabler pluginEnabler,
                                        @Nullable Runnable cleanup) throws IOException {
    return downloadPlugins(plugins, customPlugins, false, onSuccess, pluginEnabler, cleanup);
  }

  public static boolean downloadPlugins(List<PluginNode> plugins,
                                        List<? extends IdeaPluginDescriptor> customPlugins,
                                        boolean allowInstallWithoutRestart,
                                        Runnable onSuccess,
                                        PluginEnabler pluginEnabler,
                                        @Nullable Runnable cleanup) throws IOException {
    Function<Boolean, Void> function = cleanup == null ? null : aBoolean -> {
      cleanup.run();
      return null;
    };
    return downloadPlugins(plugins, customPlugins, allowInstallWithoutRestart, onSuccess, pluginEnabler, function);
  }

  public static boolean downloadPlugins(List<PluginNode> plugins,
                                        List<? extends IdeaPluginDescriptor> customPlugins,
                                        boolean allowInstallWithoutRestart,
                                        Runnable onSuccess,
                                        PluginEnabler pluginEnabler,
                                        @Nullable Function<? super Boolean, Void> function) throws IOException {
    boolean[] result = new boolean[1];
    try {
      ProgressManager.getInstance().run(new Task.Backgroundable(null, IdeBundle.message("progress.download.plugins"), true, PluginManagerUISettings.getInstance()) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            if (PluginInstaller.prepareToInstall(plugins, customPlugins, allowInstallWithoutRestart, pluginEnabler, onSuccess, indicator)) {
              result[0] = true;
            }
          }
          finally {
            if (function != null) {
              ApplicationManager.getApplication().invokeLater(() -> function.apply(result[0]));
            }
          }
        }
      });
    }
    catch (RuntimeException e) {
      if (e.getCause() != null && e.getCause() instanceof IOException) {
        throw (IOException)e.getCause();
      }
      else {
        throw e;
      }
    }
    return result[0];
  }

  public static void pluginInfoUpdate(IdeaPluginDescriptor plugin,
                                      @Nullable String filter,
                                      @NotNull JEditorPane descriptionTextArea,
                                      @NotNull PluginHeaderPanel header) {
    if (plugin == null) {
      setTextValue(null, filter, descriptionTextArea);
      header.getPanel().setVisible(false);
      return;
    }

    StringBuilder sb = new StringBuilder();
    header.setPlugin(plugin);
    String description = plugin.getDescription();
    if (!Strings.isEmptyOrSpaces(description)) {
      sb.append(description);
    }

    String changeNotes = plugin.getChangeNotes();
    if (!Strings.isEmptyOrSpaces(changeNotes)) {
      sb.append("<h4>Change Notes</h4>");
      sb.append(changeNotes);
    }

    if (!plugin.isBundled()) {
      String vendor = plugin.getVendor();
      String vendorEmail = plugin.getVendorEmail();
      String vendorUrl = plugin.getVendorUrl();
      if (!Strings.isEmptyOrSpaces(vendor) || !Strings.isEmptyOrSpaces(vendorEmail) || !Strings.isEmptyOrSpaces(vendorUrl)) {
        sb.append("<h4>Vendor</h4>");

        if (!Strings.isEmptyOrSpaces(vendor)) {
          sb.append(vendor);
        }
        if (!Strings.isEmptyOrSpaces(vendorUrl)) {
          sb.append("<br>").append(composeHref(vendorUrl));
        }
        if (!Strings.isEmptyOrSpaces(vendorEmail)) {
          sb.append("<br>")
            .append(HTML_PREFIX)
            .append("mailto:").append(vendorEmail)
            .append("\">").append(vendorEmail).append(HTML_SUFFIX);
        }
      }

      String pluginDescriptorUrl = plugin.getUrl();
      PluginInfoProvider provider = PluginInfoProvider.getInstance();
      Set<PluginId> marketplacePlugins = provider.loadCachedPlugins();
      if (marketplacePlugins == null) {
        // There are no marketplace plugins in the cache, but we should show the title anyway.
        setPluginHomePage(pluginDescriptorUrl, sb);
        // will get the marketplace plugins ids next time
        provider.loadPlugins();
      }
      else if (marketplacePlugins.contains(plugin.getPluginId())) {
        // Prevent the link to the marketplace from showing to external plugins
        setPluginHomePage(pluginDescriptorUrl, sb);
      }

      String size = plugin instanceof PluginNode ? ((PluginNode)plugin).getSize() : null;
      if (!Strings.isEmptyOrSpaces(size)) {
        sb.append("<h4>Size</h4>").append(PluginManagerColumnInfo.getFormattedSize(size));
      }
    }

    setTextValue(sb, filter, descriptionTextArea);
  }

  private static void setPluginHomePage(String pluginDescriptorUrl, StringBuilder sb){
    if (!Strings.isEmptyOrSpaces(pluginDescriptorUrl)) {
      sb.append("<h4>Plugin homepage</h4>").append(composeHref(pluginDescriptorUrl));
    }
  }

  private static void setTextValue(@Nullable StringBuilder text, @Nullable String filter, JEditorPane pane) {
    if (text != null) {
      text.insert(0, getTextPrefix());
      text.append(TEXT_SUFFIX);
      @NlsSafe String markup = SearchUtil.markup(text.toString(), filter);
      pane.setText(markup.trim());
      pane.setCaretPosition(0);
    }
    else {
      pane.setText(getTextPrefix() + TEXT_SUFFIX);
    }
  }

  private static String composeHref(String vendorUrl) {
    return HTML_PREFIX + vendorUrl + "\">" + vendorUrl + HTML_SUFFIX;
  }

  public static class MyHyperlinkListener implements HyperlinkListener {
    @Override
    public void hyperlinkUpdate(HyperlinkEvent e) {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        JEditorPane pane = (JEditorPane)e.getSource();
        if (e instanceof HTMLFrameHyperlinkEvent) {
          HTMLFrameHyperlinkEvent evt = (HTMLFrameHyperlinkEvent)e;
          HTMLDocument doc = (HTMLDocument)pane.getDocument();
          doc.processHTMLFrameHyperlinkEvent(evt);
        }
        else {
          URL url = e.getURL();
          if (url != null) {
            BrowserUtil.browse(url);
          }
        }
      }
    }
  }

  public static boolean isAccepted(@Nullable String filter, @NotNull Set<String> search, @NotNull IdeaPluginDescriptor descriptor) {
    if (Strings.isEmpty(filter) ||
        StringUtil.indexOfIgnoreCase(descriptor.getName(), filter, 0) >= 0 ||
        isAccepted(search, filter, descriptor.getName())) {
      return true;
    }
    if (isAccepted(search, filter, descriptor.getDescription())) {
      return true;
    }

    String category = descriptor.getCategory();
    return category != null && (StringUtil.containsIgnoreCase(category, filter) || isAccepted(search, filter, category));
  }

  public static boolean isAccepted(@NotNull Set<String> search, @NotNull String filter, @Nullable String description) {
    if (Strings.isEmpty(description) || filter.length() <= 2) {
      return false;
    }

    Set<String> words = SearchableOptionsRegistrar.getInstance().getProcessedWords(description);
    if (words.contains(filter)) {
      return true;
    }

    if (search.isEmpty()) {
      return false;
    }

    Set<String> descriptionSet = new HashSet<>(search);
    descriptionSet.removeAll(words);
    return descriptionSet.isEmpty();
  }

  public static boolean suggestToEnableInstalledDependantPlugins(@NotNull PluginEnabler pluginEnabler, @NotNull List<? extends IdeaPluginDescriptor> list) {
    Set<IdeaPluginDescriptor> disabled = new HashSet<>();
    Set<IdeaPluginDescriptor> disabledDependants = new HashSet<>();
    for (IdeaPluginDescriptor node : list) {
      PluginId pluginId = node.getPluginId();
      if (pluginEnabler.isDisabled(pluginId)) {
        disabled.add(node);
      }
      for (IdeaPluginDependency dependency : node.getDependencies()) {
        if (dependency.isOptional()) {
          continue;
        }

        PluginId dependantId = dependency.getPluginId();
        if (PluginManagerCore.isModuleDependency(dependantId)) {
          // If there is no installed plugin implementing module then it can only be platform module which can not be disabled
          if (PluginManagerCore.findPluginByModuleDependency(dependantId) == null) continue;
        }
        IdeaPluginDescriptor pluginDescriptor = PluginManagerCore.getPlugin(dependantId);
        if (pluginDescriptor != null && pluginEnabler.isDisabled(dependantId)) {
          disabledDependants.add(pluginDescriptor);
        }
      }
    }

    if (!disabled.isEmpty() || !disabledDependants.isEmpty()) {
      String message = "";
      if (disabled.size() == 1) {
        message += IdeBundle.message("plugin.manager.main.suggest.to.enable.message.part1", disabled.iterator().next().getName());
      }
      else if (!disabled.isEmpty()) {
        message += IdeBundle.message("plugin.manager.main.suggest.to.enable.message.part2", StringUtil.join(disabled, pluginDescriptor -> pluginDescriptor.getName(), ", "));
      }

      if (!disabledDependants.isEmpty()) {
        message += "<br>";
        message += IdeBundle.message("plugin.manager.main.suggest.to.enable.message.part3", list.size());
        message += " ";
        if (disabledDependants.size() == 1) {
          message += IdeBundle.message("plugin.manager.main.suggest.to.enable.message.part4", disabledDependants.iterator().next().getName());
        }
        else {
          message += IdeBundle.message("plugin.manager.main.suggest.to.enable.message.part5", StringUtil.join(disabledDependants, pluginDescriptor -> pluginDescriptor.getName(), ", "));
        }
      }
      message += " ";
      message += IdeBundle.message(disabled.isEmpty() ? "plugin.manager.main.suggest.to.enable.message.part6" : "plugin.manager.main.suggest.to.enable.message.part7");

      boolean result;
      if (!disabled.isEmpty() && !disabledDependants.isEmpty()) {
        int code =
          MessageDialogBuilder.yesNoCancel(IdeBundle.message("dialog.title.dependent.plugins.found"), XmlStringUtil.wrapInHtml(message))
            .yesText(IdeBundle.message("button.enable.all"))
            .noText(IdeBundle.message("button.enable.updated.plugin.0", disabled.size()))
            .guessWindowAndAsk();
        if (code == Messages.CANCEL) {
          return false;
        }
        result = code == Messages.YES;
      }
      else {
        message += "<br>";
        if (!disabled.isEmpty()) {
          message += IdeBundle.message("plugin.manager.main.suggest.to.enable.message.part8", disabled.size());
        }
        else {
          message += IdeBundle.message("plugin.manager.main.suggest.to.enable.message.part9", disabledDependants.size());
        }
        message += "?";
        result = MessageDialogBuilder.yesNo(IdeBundle.message("dialog.title.dependent.plugins.found"), XmlStringUtil.wrapInHtml(message)).guessWindowAndAsk();
        if (!result) {
          return false;
        }
      }

      if (result) {
        disabled.addAll(disabledDependants);
        pluginEnabler.enablePlugins(disabled);
      }
      else if (!disabled.isEmpty()) {
        pluginEnabler.enablePlugins(disabled);
      }
      return true;
    }
    return false;
  }

  public interface PluginEnabler {
    void enablePlugins(@NotNull Set<? extends IdeaPluginDescriptor> plugins);

    void disablePlugins(@NotNull Set<? extends IdeaPluginDescriptor> plugins);

    boolean isDisabled(@NotNull PluginId pluginId);

    class HEADLESS implements PluginEnabler {
      @Override
      public void enablePlugins(@NotNull Set<? extends IdeaPluginDescriptor> plugins) {
        DisabledPluginsState.enablePlugins(plugins, true);
      }

      @Override
      public void disablePlugins(@NotNull Set<? extends IdeaPluginDescriptor> plugins) {
        for (IdeaPluginDescriptor descriptor : plugins) {
          PluginManagerCore.disablePlugin(descriptor.getPluginId());
        }
      }

      @Override
      public boolean isDisabled(@NotNull PluginId pluginId) {
        return PluginManagerCore.isDisabled(pluginId);
      }
    }
  }

  public static void notifyPluginsUpdated(@Nullable Project project) {
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    String title = IdeBundle.message("updates.notification.title", ApplicationNamesInfo.getInstance().getFullProductName());
    String action = IdeBundle.message("ide.restart.required.notification", app.isRestartCapable() ? 1 : 0);
    Notification notification = UpdateChecker.getNotificationGroup().createNotification(title, "", NotificationType.INFORMATION, null, "plugins.updated.suggest.restart");
    notification.addAction(new NotificationAction(action) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        if (PluginManagerConfigurable.showRestartDialog() == Messages.YES) {
          notification.expire();
          ApplicationManagerEx.getApplicationEx().restart(true);
        }
      }
    });
    notification.notify(project);
  }

  public static boolean checkThirdPartyPluginsAllowed(Iterable<? extends IdeaPluginDescriptor> descriptors) {
    UpdateSettings updateSettings = UpdateSettings.getInstance();

    if (updateSettings.isThirdPartyPluginsAllowed()) {
      return true;
    }

    PluginManager pluginManager = PluginManager.getInstance();
    for (IdeaPluginDescriptor descriptor : descriptors) {
      if (!pluginManager.isDevelopedByJetBrains(descriptor)) {
        String title = IdeBundle.message("third.party.plugins.privacy.note.title");
        String message = IdeBundle.message("third.party.plugins.privacy.note.message");
        String yesText = IdeBundle.message("third.party.plugins.privacy.note.yes");
        String noText = IdeBundle.message("third.party.plugins.privacy.note.no");
        if (Messages.showYesNoDialog(message, title, yesText, noText, Messages.getWarningIcon()) == Messages.YES) {
          updateSettings.setThirdPartyPluginsAllowed(true);
          return true;
        } else {
          return false;
        }
      }
    }

    return true;
  }
}
