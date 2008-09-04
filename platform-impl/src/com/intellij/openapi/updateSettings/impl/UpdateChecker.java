/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Oct 31, 2002
 * Time: 6:33:01 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.reporter.ConnectionException;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.text.DateFormatUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * XML sample:
 * <idea>
 * <build>456</build>
 * <version>4.5.2</version>
 * <title>New Intellij IDEA Version</title>
 * <message>
 * New version of IntelliJ IDEA is available.
 * Please visit http://www.intellij.com/ for more info.
 * </message>
 * </idea>
 */
public final class UpdateChecker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.updateSettings.impl.UpdateChecker");

  private static long checkInterval = 0;
  private static boolean myVeryFirstOpening = true;
  @NonNls private static final String BUILD_NUMBER_STUB = "__BUILD_NUMBER__";
  @NonNls private static final String ELEMENT_BUILD = "build";
  @NonNls private static final String ELEMENT_VERSION = "version";

  @NonNls
  private static final String DISABLED_UPDATE = "disabled_update.txt";
  private static TreeSet<String> ourDisabledToUpdatePlugins;

  private static class StringHolder {
    private static final String UPDATE_URL = ApplicationInfoEx.getInstanceEx().getUpdateUrls().getCheckingUrl();
  }

  private static String getUpdateUrl() {
    return StringHolder.UPDATE_URL;
  }

  public static boolean isMyVeryFirstOpening() {
    return myVeryFirstOpening;
  }

  public static void setMyVeryFirstOpening(final boolean myVeryFirstProjectOpening) {
    myVeryFirstOpening = myVeryFirstProjectOpening;
  }

  public static boolean checkNeeded() {

    final UpdateSettingsConfigurable settings = UpdateSettingsConfigurable.getInstance();
    if (settings == null || getUpdateUrl() == null) return false;

    final String checkPeriod = settings.CHECK_PERIOD;
    if (checkPeriod.equals(UpdateSettingsConfigurable.ON_START_UP)) {
      checkInterval = 0;
    }
    if (checkPeriod.equals(UpdateSettingsConfigurable.DAILY)) {
      checkInterval = DateFormatUtil.DAY;
    }
    if (settings.CHECK_PERIOD.equals(UpdateSettingsConfigurable.WEEKLY)) {
      checkInterval = DateFormatUtil.WEEK;
    }
    if (settings.CHECK_PERIOD.equals(UpdateSettingsConfigurable.MONTHLY)) {
      checkInterval = DateFormatUtil.MONTH;
    }

    final long timeDelta = System.currentTimeMillis() - settings.LAST_TIME_CHECKED;
    if (Math.abs(timeDelta) < checkInterval) return false;

    return settings.CHECK_NEEDED;
  }

  public static List<PluginDownloader> updatePlugins(final boolean showErrorDialog) {
    final List<PluginDownloader> downloaded = new ArrayList<PluginDownloader>();
    final Set<String> failed = new HashSet<String>();
    for (String host : UpdateSettingsConfigurable.getInstance().getPluginHosts()) {
      try {
        checkPluginsHost(host, downloaded);
      }
      catch (Exception e) {
        LOG.info(e);
        failed.add(host);
      }
    }
    if (!failed.isEmpty()) {
      final String failedMessage = IdeBundle.message("connection.failed.message", StringUtil.join(failed, ","));
      if (showErrorDialog) {
        Messages.showErrorDialog(failedMessage, IdeBundle.message("title.connection.error"));
      } else {
        LOG.info(failedMessage);
      }
    }
    return downloaded.isEmpty() ? null : downloaded;
  }

  public static boolean checkPluginsHost(final String host, final List<PluginDownloader> downloaded) throws Exception {
    final Document document = loadVersionInfo(host);
    if (document == null) return false;
    boolean success = true;
    for (Object plugin : document.getRootElement().getChildren("plugin")) {
      Element pluginElement = (Element)plugin;
      final String pluginId = pluginElement.getAttributeValue("id");
      final String pluginUrl = pluginElement.getAttributeValue("url");
      final String pluginVersion = pluginElement.getAttributeValue("version");
      if (pluginId == null) {
        LOG.info("plugin id should not be null");
        success = false;
        continue;
      }

      if (pluginUrl == null) {
        LOG.info("plugin url should not be null");
        success = false;
        continue;
      }

      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable(){
        public void run() {
          try {
            final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
            if (progressIndicator != null) {
              progressIndicator.setText(pluginUrl);
            }
            final PluginDownloader uploader = new PluginDownloader(pluginId, pluginUrl, pluginVersion);
            if (uploader.prepareToInstall()) {
              downloaded.add(uploader);
            }
          }
          catch (IOException e) {
            LOG.info(e);
          }
        }
      }, IdeBundle.message("update.uploading.plugin.progress.title"), true, null);
    }
    return success;
  }


  @Nullable
  public static NewVersion checkForUpdates() throws ConnectionException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: checkForUpdates()");
    }

    final Document document;
    try {
      document = loadVersionInfo(getUpdateUrl());
      if (document == null) return null;
    }
    catch (Throwable t) {
      LOG.debug(t);
      throw new ConnectionException(t);
    }

    final String availBuild = document.getRootElement().getChild(ELEMENT_BUILD).getTextTrim();
    final String availVersion = document.getRootElement().getChild(ELEMENT_VERSION).getTextTrim();
    String ourBuild = ApplicationInfo.getInstance().getBuildNumber().trim();
    if (BUILD_NUMBER_STUB.equals(ourBuild)) ourBuild = Integer.toString(Integer.MAX_VALUE);

    if (LOG.isDebugEnabled()) {
      LOG.debug("build available:'" + availBuild + "' ourBuild='" + ourBuild + "' ");
    }

    try {
      final int iAvailBuild = Integer.parseInt(availBuild);
      final int iOurBuild = Integer.parseInt(ourBuild);
      if (iAvailBuild > iOurBuild) {
        return new NewVersion(iAvailBuild, availVersion);
      }
      return null;
    }
    catch (Throwable t) {
      LOG.debug(t);
      return null;
    }
    finally {
      UpdateSettingsConfigurable.getInstance().LAST_TIME_CHECKED = System.currentTimeMillis();
    }
  }

  private static Document loadVersionInfo(final String url) throws Exception {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: loadVersionInfo(UPDATE_URL='" + url + "' )");
    }
    final Document[] document = new Document[] {null};
    final Exception[] exception = new Exception[] {null};
    Future<?> downloadThreadFuture = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        try {
          HttpConfigurable.getInstance().prepareURL(url);
          final InputStream inputStream = new URL(url).openStream();
          try {
            document[0] = JDOMUtil.loadDocument(inputStream);
          }
          finally {
            inputStream.close();
          }
        }
        catch (IOException e) {
          exception[0] = e;
        }
        catch (JDOMException e) {
          LOG.info(e); // Broken xml downloaded. Don't bother telling user.
        }
      }
    });

    try {
      downloadThreadFuture.get(5, TimeUnit.SECONDS);
    }
    catch (TimeoutException e) {
    }

    if (!downloadThreadFuture.isDone()) {
      downloadThreadFuture.cancel(true);
      throw new ConnectionException(IdeBundle.message("updates.timeout.error"));
    }

    if (exception[0] != null) throw exception[0];
    return document[0];
  }

  public static void showNoUpdatesDialog(boolean enableLink, final List<PluginDownloader> updatePlugins) {
    NoUpdatesDialog dialog = new NoUpdatesDialog(true, updatePlugins, enableLink);
    dialog.show();
  }

  public static void showUpdateInfoDialog(boolean enableLink, final NewVersion version, final List<PluginDownloader> updatePlugins) {
    UpdateInfoDialog dialog = new UpdateInfoDialog(true, version, updatePlugins, enableLink);
    dialog.show();
  }


  public static boolean install(List<PluginDownloader> downloaders) {
    boolean installed = false;
    for (PluginDownloader downloader : downloaders) {
      if (getDisabledToUpdatePlugins().contains(downloader.getPluginId())) continue;
      try {
        downloader.install();
        installed = true;
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    return installed;
  }

  public static class NewVersion {
    private final int latestBuild;
    private final String latestVersion;

    public int getLatestBuild() {
      return latestBuild;
    }

    public String getLatestVersion() {
      return latestVersion;
    }

    public NewVersion(int build, String version) {
      latestBuild = build;
      latestVersion = version;
    }
  }

  public static Set<String> getDisabledToUpdatePlugins() {
    if (ourDisabledToUpdatePlugins == null) {
      ourDisabledToUpdatePlugins = new TreeSet<String>();
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        try {
          final File file = new File(PathManager.getConfigPath(), DISABLED_UPDATE);
          if (file.isFile()) {
            final String[] ids = new String(FileUtil.loadFileText(file)).split("[\\s]");
            for (String id : ids) {
              if (id != null && id.trim().length() > 0) {
                ourDisabledToUpdatePlugins.add(id.trim());
              }
            }
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
    return ourDisabledToUpdatePlugins;
  }

  public static void saveDisabledToUpdatePlugins() {
    try {
      File plugins = new File(PathManager.getConfigPath(), DISABLED_UPDATE);
      if (!plugins.isFile()) {
        plugins.createNewFile();
      }
      PrintWriter printWriter = null;
      try {
        printWriter = new PrintWriter(new BufferedWriter(new FileWriter(plugins)));
        for (String id : getDisabledToUpdatePlugins()) {
          printWriter.println(id);
        }
        printWriter.flush();
      }
      finally {
        if (printWriter != null) {
          printWriter.close();
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }
}
