/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.browsers;

import com.intellij.CommonBundle;
import com.intellij.Patches;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.ui.GuiUtils;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.io.ZipUtil;
import com.intellij.util.ui.OptionsDialog;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class BrowserLauncherAppless extends BrowserLauncher {
  static final Logger LOG = Logger.getInstance(BrowserLauncherAppless.class);

  private static boolean isDesktopActionSupported(Desktop.Action action) {
    return !Patches.SUN_BUG_ID_6457572 && !Patches.SUN_BUG_ID_6486393 &&
           Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(action);
  }

  public static boolean canUseSystemDefaultBrowserPolicy() {
    return isDesktopActionSupported(Desktop.Action.BROWSE) ||
           SystemInfo.isMac || SystemInfo.isWindows ||
           (SystemInfo.isUnix && SystemInfo.hasXdgOpen());
  }

  private static GeneralSettings getGeneralSettingsInstance() {
    if (ApplicationManager.getApplication() != null) {
      GeneralSettings settings = GeneralSettings.getInstance();
      if (settings != null) {
        return settings;
      }
    }

    return new GeneralSettings();
  }

  @Nullable
  private static List<String> getDefaultBrowserCommand() {
    if (SystemInfo.isWindows) {
      return Arrays.asList(ExecUtil.getWindowsShellName(), "/c", "start", GeneralCommandLine.inescapableQuote(""));
    }
    else if (SystemInfo.isMac) {
      return Collections.singletonList(ExecUtil.getOpenCommandPath());
    }
    else if (SystemInfo.isUnix && SystemInfo.hasXdgOpen()) {
      return Collections.singletonList("xdg-open");
    }
    else {
      return null;
    }
  }

  @Override
  public void open(@NotNull String url) {
    openOrBrowse(url, false, null);
  }

  @Override
  public void browse(@NotNull File file) {
    browse(VfsUtil.toUri(file));
  }

  @Override
  public void browse(@NotNull URI uri) {
    browse(uri, null);
  }

  public void browse(@NotNull URI uri, @Nullable Project project) {
    LOG.debug("Launch browser: [" + uri + "]");

    GeneralSettings settings = getGeneralSettingsInstance();
    if (settings.isUseDefaultBrowser()) {
      boolean tryToUseCli = true;
      if (isDesktopActionSupported(Desktop.Action.BROWSE)) {
        try {
          Desktop.getDesktop().browse(uri);
          LOG.debug("Browser launched using JDK 1.6 API");
          return;
        }
        catch (Exception e) {
          LOG.warn("Error while using Desktop API, fallback to CLI", e);
          // if "No application knows how to open", then we must not try to use OS open
          tryToUseCli = !e.getMessage().contains("Error code: -10814");
        }
      }

      if (tryToUseCli) {
        List<String> command = getDefaultBrowserCommand();
        if (command != null) {
          doLaunch(uri.toString(), command, null, project, ArrayUtil.EMPTY_STRING_ARRAY, null);
          return;
        }
      }
    }

    browseUsingNotSystemDefaultBrowserPolicy(uri, settings, project);
  }

  protected void browseUsingNotSystemDefaultBrowserPolicy(@NotNull URI uri, @NotNull GeneralSettings settings, @Nullable Project project) {
    browseUsingPath(uri.toString(), settings.getBrowserPath(), null, project, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  private void openOrBrowse(@NotNull String url, boolean browse, @Nullable Project project) {
    url = url.trim();

    if (url.startsWith("jar:")) {
      String files = extractFiles(url);
      if (files == null) {
        return;
      }
      url = files;
    }

    URI uri;
    if (BrowserUtil.isAbsoluteURL(url)) {
      uri = VfsUtil.toUri(url);
    }
    else {
      File file = new File(url);
      if (!browse && isDesktopActionSupported(Desktop.Action.OPEN)) {
        if (!file.exists()) {
          showError(IdeBundle.message("error.file.does.not.exist", file.getPath()), null, null, null, null);
          return;
        }

        try {
          Desktop.getDesktop().open(file);
          return;
        }
        catch (IOException e) {
          LOG.debug(e);
        }
      }

      browse(file);
      return;
    }

    if (uri == null) {
      showError(IdeBundle.message("error.malformed.url", url), null, project, null, null);
    }
    else {
      browse(uri, project);
    }
  }

  @Nullable
  private static String extractFiles(String url) {
    try {
      int sharpPos = url.indexOf('#');
      String anchor = "";
      if (sharpPos != -1) {
        anchor = url.substring(sharpPos);
        url = url.substring(0, sharpPos);
      }

      Pair<String, String> pair = URLUtil.splitJarUrl(url);
      if (pair == null) return null;

      File jarFile = new File(FileUtil.toSystemDependentName(pair.first));
      if (!jarFile.canRead()) return null;

      String jarUrl = StandardFileSystems.FILE_PROTOCOL_PREFIX + FileUtil.toSystemIndependentName(jarFile.getPath());
      String jarLocationHash = jarFile.getName() + "." + Integer.toHexString(jarUrl.hashCode());
      final File outputDir = new File(getExtractedFilesDir(), jarLocationHash);

      final String currentTimestamp = String.valueOf(new File(jarFile.getPath()).lastModified());
      final File timestampFile = new File(outputDir, ".idea.timestamp");

      String previousTimestamp = null;
      if (timestampFile.exists()) {
        previousTimestamp = FileUtilRt.loadFile(timestampFile);
      }

      if (!currentTimestamp.equals(previousTimestamp)) {
        final Ref<Boolean> extract = new Ref<Boolean>();
        Runnable r = new Runnable() {
          @Override
          public void run() {
            final ConfirmExtractDialog dialog = new ConfirmExtractDialog();
            if (dialog.isToBeShown()) {
              dialog.show();
              extract.set(dialog.isOK());
            }
            else {
              dialog.close(DialogWrapper.OK_EXIT_CODE);
              extract.set(true);
            }
          }
        };

        try {
          GuiUtils.runOrInvokeAndWait(r);
        }
        catch (InvocationTargetException ignored) {
          extract.set(false);
        }
        catch (InterruptedException ignored) {
          extract.set(false);
        }

        if (!extract.get()) {
          return null;
        }

        boolean closeZip = true;
        final ZipFile zipFile = new ZipFile(jarFile);
        try {
          ZipEntry entry = zipFile.getEntry(pair.second);
          if (entry == null) {
            return null;
          }
          InputStream is = zipFile.getInputStream(entry);
          ZipUtil.extractEntry(entry, is, outputDir);
          closeZip = false;
        }
        finally {
          if (closeZip) {
            zipFile.close();
          }
        }

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            new Task.Backgroundable(null, "Extracting files...", true) {
              @Override
              public void run(@NotNull final ProgressIndicator indicator) {
                final int size = zipFile.size();
                final int[] counter = new int[]{0};

                class MyFilter implements FilenameFilter {
                  private final Set<File> myImportantDirs = ContainerUtil.newHashSet(outputDir, new File(outputDir, "resources"));
                  private final boolean myImportantOnly;

                  private MyFilter(boolean importantOnly) {
                    myImportantOnly = importantOnly;
                  }

                  @Override
                  public boolean accept(@NotNull File dir, @NotNull String name) {
                    indicator.checkCanceled();
                    boolean result = myImportantOnly == myImportantDirs.contains(dir);
                    if (result) {
                      indicator.setFraction(((double)counter[0]) / size);
                      counter[0]++;
                    }
                    return result;
                  }
                }

                try {
                  try {
                    ZipUtil.extract(zipFile, outputDir, new MyFilter(true));
                    ZipUtil.extract(zipFile, outputDir, new MyFilter(false));
                    FileUtil.writeToFile(timestampFile, currentTimestamp);
                  }
                  finally {
                    zipFile.close();
                  }
                }
                catch (IOException ignore) { }
              }
            }.queue();
          }
        });
      }

      return VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(new File(outputDir, pair.second).getPath())) + anchor;
    }
    catch (IOException e) {
      LOG.warn(e);
      Messages.showErrorDialog("Cannot extract files: " + e.getMessage(), "Error");
      return null;
    }
  }

  private static File getExtractedFilesDir() {
    return new File(PathManager.getSystemPath(), "ExtractedFiles");
  }

  public static void clearExtractedFiles() {
    FileUtil.delete(getExtractedFilesDir());
  }

  private static class ConfirmExtractDialog extends OptionsDialog {
    private ConfirmExtractDialog() {
      super(null);
      setTitle("Confirmation");
      init();
    }

    @Override
    protected boolean isToBeShown() {
      return getGeneralSettingsInstance().isConfirmExtractFiles();
    }

    @Override
    protected void setToBeShown(boolean value, boolean onOk) {
      getGeneralSettingsInstance().setConfirmExtractFiles(value);
    }

    @Override
    protected boolean shouldSaveOptionsOnCancel() {
      return true;
    }

    @Override
    @NotNull
    protected Action[] createActions() {
      setOKButtonText(CommonBundle.getYesButtonText());
      return new Action[]{getOKAction(), getCancelAction()};
    }

    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout());
      String message = "The files are inside an archive, do you want them to be extracted?";
      JLabel label = new JLabel(message);

      label.setIconTextGap(10);
      label.setIcon(Messages.getQuestionIcon());

      panel.add(label, BorderLayout.CENTER);
      panel.add(Box.createVerticalStrut(10), BorderLayout.SOUTH);

      return panel;
    }
  }

  @Override
  public void browse(@NotNull String url, @Nullable WebBrowser browser) {
    browse(url, browser, null);
  }

  @Override
  public void browse(@NotNull String url, @Nullable WebBrowser browser, @Nullable Project project) {
    if (browser == null) {
      openOrBrowse(url, true, project);
    }
    else {
      for (UrlOpener urlOpener : UrlOpener.EP_NAME.getExtensions()) {
        if (urlOpener.openUrl(browser, url, project)) {
          return;
        }
      }
    }
  }

  @Override
  public boolean browseUsingPath(@Nullable final String url,
                                 @Nullable String browserPath,
                                 @Nullable final WebBrowser browser,
                                 @Nullable final Project project,
                                 @NotNull final String[] additionalParameters) {
    Runnable launchTask = null;
    if (browserPath == null && browser != null) {
      browserPath = PathUtil.toSystemDependentName(browser.getPath());
      launchTask = new Runnable() {
        @Override
        public void run() {
          browseUsingPath(url, null, browser, project, additionalParameters);
        }
      };
    }
    return doLaunch(url, browserPath, browser, project, additionalParameters, launchTask);
  }

  private boolean doLaunch(@Nullable String url,
                           @Nullable String browserPath,
                           @Nullable WebBrowser browser,
                           @Nullable Project project,
                           @NotNull String[] additionalParameters,
                           @Nullable Runnable launchTask) {
    if (!checkPath(browserPath, browser, project, launchTask)) {
      return false;
    }
    return doLaunch(url, BrowserUtil.getOpenBrowserCommand(browserPath, false), browser, project, additionalParameters, launchTask);
  }

  @Contract("null, _, _, _ -> false")
  public boolean checkPath(@Nullable String browserPath, @Nullable WebBrowser browser, @Nullable Project project, @Nullable Runnable launchTask) {
    if (!StringUtil.isEmptyOrSpaces(browserPath)) {
      return true;
    }

    String message = browser != null ? browser.getBrowserNotFoundMessage() :
                     IdeBundle.message("error.please.specify.path.to.web.browser", CommonBundle.settingsActionPath());
    showError(message, browser, project, IdeBundle.message("title.browser.not.found"), launchTask);
    return false;
  }

  private boolean doLaunch(@Nullable String url,
                           @NotNull List<String> command,
                           @Nullable WebBrowser browser,
                           @Nullable Project project,
                           @NotNull String[] additionalParameters,
                           @Nullable Runnable launchTask) {
    GeneralCommandLine commandLine = new GeneralCommandLine(command);

    if (url != null && url.startsWith("jar:")) {
      String files = extractFiles(url);
      if (files == null) {
        return false;
      }
      url = files;
    }

    if (url != null) {
      commandLine.addParameter(url);
    }

    final BrowserSpecificSettings browserSpecificSettings = browser == null ? null : browser.getSpecificSettings();
    if (browserSpecificSettings != null) {
      commandLine.getEnvironment().putAll(browserSpecificSettings.getEnvironmentVariables());
    }

    addArgs(commandLine, browserSpecificSettings, additionalParameters);

    try {
      Process process = commandLine.createProcess();
      checkCreatedProcess(browser, project, commandLine, process, launchTask);
      return true;
    }
    catch (ExecutionException e) {
      showError(e.getMessage(), browser, project, null, null);
      return false;
    }
  }

  protected void checkCreatedProcess(@Nullable WebBrowser browser,
                                     @Nullable Project project,
                                     @NotNull GeneralCommandLine commandLine,
                                     @NotNull Process process,
                                     @Nullable Runnable launchTask) {
  }

  protected void showError(@Nullable String error, @Nullable WebBrowser browser, @Nullable Project project, String title, @Nullable Runnable launchTask) {
    // Not started yet. Not able to show message up. (Could happen in License panel under Linux).
    LOG.warn(error);
  }

  private static void addArgs(@NotNull GeneralCommandLine command, @Nullable BrowserSpecificSettings settings, @NotNull String[] additional) {
    List<String> specific = settings == null ? Collections.<String>emptyList() : settings.getAdditionalParameters();
    if (specific.size() + additional.length > 0) {
      if (isOpenCommandUsed(command)) {
        if (BrowserUtil.isOpenCommandSupportArgs()) {
          command.addParameter("--args");
        }
        else {
          LOG.warn("'open' command doesn't allow to pass command line arguments so they will be ignored: " +
                   StringUtil.join(specific, ", ") + " " + Arrays.toString(additional));
          return;
        }
      }

      command.addParameters(specific);
      command.addParameters(additional);
    }
  }

  public static boolean isOpenCommandUsed(@NotNull GeneralCommandLine command) {
    return SystemInfo.isMac && ExecUtil.getOpenCommandPath().equals(command.getExePath());
  }
}