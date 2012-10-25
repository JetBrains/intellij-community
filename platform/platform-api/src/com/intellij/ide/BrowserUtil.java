/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.CommonBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.ui.GuiUtils;
import com.intellij.util.SmartList;
import com.intellij.util.io.ZipUtil;
import com.intellij.util.ui.OptionsDialog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class BrowserUtil {
  private static final Logger LOG = Logger.getInstance("#" + BrowserUtil.class.getName());

  private static final boolean TRIM_URLS = !"false".equalsIgnoreCase(System.getProperty("idea.browse.trim.urls"));

  // The pattern for 'scheme' mainly according to RFC1738.
  // We have to violate the RFC since we need to distinguish
  // real schemes from local Windows paths; The only difference
  // with RFC is that we do not allow schemes with length=1 (in other case
  // local paths like "C:/temp/index.html" would be erroneously interpreted as
  // external URLs.)
  private static final Pattern ourExternalPrefix = Pattern.compile("^[\\w\\+\\.\\-]{2,}:");
  private static final Pattern ourAnchorSuffix = Pattern.compile("#(.*)$");

  private BrowserUtil() { }

  public static boolean isAbsoluteURL(String url) {
    return ourExternalPrefix.matcher(url.toLowerCase()).find();
  }

  public static String getDocURL(String url) {
    Matcher anchorMatcher = ourAnchorSuffix.matcher(url);

    if (anchorMatcher.find()) {
      return anchorMatcher.reset().replaceAll("");
    }

    return url;
  }

  @Nullable
  public static URL getURL(String url) throws MalformedURLException {
    if (!isAbsoluteURL(url)) {
      return new URL("file", "", url);
    }

    return VfsUtil.convertToURL(url);
  }

  /**
   * Main method: tries to launch a browser using every possible way.
   *
   * @param url an URL to open.
   */
  public static void launchBrowser(@NotNull @NonNls String url) {
    LOG.debug("Launch browser: [" + url + "]");

    if (TRIM_URLS) {
      url = url.trim();
    }

    if (url.startsWith("jar:")) {
      url = extractFiles(url);
      if (url == null) return;
    }

    if (getGeneralSettingsInstance().isUseDefaultBrowser() && canStartDefaultBrowser()) {
      final List<String> command = getDefaultBrowserCommand();
      if (command != null) {
        launchBrowserByCommand(url, command);
      }
      else {
        launchBrowserUsingDesktopApi(url);
      }
    }
    else {
      launchBrowserUsingStandardWay(url);
    }
  }

  private static GeneralSettings getGeneralSettingsInstance() {
    final GeneralSettings settings = ApplicationManager.getApplication() != null ? GeneralSettings.getInstance() : null;
    return settings != null ? settings : new GeneralSettings();
  }

  public static boolean canStartDefaultBrowser() {
    if (SystemInfo.isMac || SystemInfo.isWindows) {
      return true;
    }
    else if (SystemInfo.isUnix && SystemInfo.hasXdgOpen()) {
      return true;
    }
    else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
      return true;
    }

    return false;
  }

  @Nullable
  @NonNls
  private static List<String> getDefaultBrowserCommand() {
    if (SystemInfo.isWindows) {
      return getOpenBrowserWinCommand(null);
    }
    else if (SystemInfo.isMac) {
      return new SmartList<String>(ExecUtil.getOpenCommandPath());
    }
    else if (SystemInfo.isUnix && SystemInfo.hasXdgOpen()) {
      return new SmartList<String>("xdg-open");
    }

    return null;
  }

  private static void launchBrowserByCommand(final String url, @NotNull final List<String> command) {
    URL curl;
    try {
      curl = getURL(url);
    }
    catch (MalformedURLException ignored) {
      curl = null;
    }
    if (curl == null) {
      showErrorMessage(IdeBundle.message("error.malformed.url", url), CommonBundle.getErrorTitle());
      return;
    }

    try {
      final GeneralCommandLine commandLine = new GeneralCommandLine(command);
      commandLine.addParameter(escapeUrl(curl.toString()));
      if (SystemInfo.isWindows) {
        commandLine.putUserData(GeneralCommandLine.DO_NOT_ESCAPE_QUOTES, true);
      }
      commandLine.createProcess();

      if (LOG.isDebugEnabled()) {
        LOG.debug("Browser launched with command line: " + commandLine);
      }
    }
    catch (ExecutionException e) {
      showErrorMessage(IdeBundle.message("error.cannot.start.browser", e.getMessage()), CommonBundle.getErrorTitle());
    }
  }

  @NotNull
  public static String escapeUrl(@NotNull @NonNls String url) {
    return SystemInfo.isWindows ? "\"" + url + "\""
                                : url;
  }

  private static boolean launchBrowserUsingDesktopApi(final String sUrl) {
    try {
      URL url = getURL(sUrl);
      if (url == null) return false;
      Desktop.getDesktop().browse(url.toURI());
      LOG.debug("Browser launched using JDK 1.6 API");
      return true;
    }
    catch (Exception e) {
      return false;
    }
  }

  private static void launchBrowserUsingStandardWay(final String url) {
    final String browserPath = getGeneralSettingsInstance().getBrowserPath();
    if (StringUtil.isEmptyOrSpaces(browserPath)) {
      showErrorMessage(IdeBundle.message("error.please.specify.path.to.web.browser"), IdeBundle.message("title.browser.not.found"));
      return;
    }

    launchBrowserByCommand(url, getOpenBrowserCommand(browserPath));
  }

  private static List<String> getOpenBrowserWinCommand(@Nullable String browserPath) {
    ArrayList<String> command = new ArrayList<String>();
    command.add(SystemInfo.isWindows9x ? "command.com" : "cmd.exe");
    command.add("/c");
    command.add("start");
    command.add("\"\"");
    if (browserPath != null) {
      command.add(browserPath);
    }
    return command;
  }

  public static List<String> getOpenBrowserCommand(final @NonNls @NotNull String browserPath) {
    if (SystemInfo.isMac && !new File(browserPath).isFile()) {
      ArrayList<String> command = new ArrayList<String>();
      command.add(ExecUtil.getOpenCommandPath());
      command.add("-a");
      command.add(browserPath);
      return command;
    }
    else if (SystemInfo.isWindows && !new File(browserPath).isFile()) {
      return getOpenBrowserWinCommand(browserPath);
    }
    else {
      return new SmartList<String>(browserPath);
    }
  }

  private static void showErrorMessage(final String message, final String title) {
    final Application app = ApplicationManager.getApplication();
    if (app == null) {
      return; // Not started yet. Not able to show message up. (Could happen in License panel under Linux).
    }

    Runnable runnable = new Runnable() {
      public void run() {
        Messages.showMessageDialog(message, title, Messages.getErrorIcon());
      }
    };

    if (app.isDispatchThread()) {
      runnable.run();
    }
    else {
      app.invokeLater(runnable, ModalityState.NON_MODAL);
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

      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      if (file == null || !(file.getFileSystem() instanceof JarFileSystem)) return null;

      JarFileSystem jarFileSystem = (JarFileSystem)file.getFileSystem();
      VirtualFile jarVirtualFile = jarFileSystem.getVirtualFileForJar(file);
      if (jarVirtualFile == null) return null;

      String targetFilePath = file.getPath();
      String targetFileRelativePath = StringUtil.substringAfter(targetFilePath, JarFileSystem.JAR_SEPARATOR);

      String jarVirtualFileLocationHash = jarVirtualFile.getName() + Integer.toHexString(jarVirtualFile.getUrl().hashCode());
      final File outputDir = new File(getExtractedFilesDir(), jarVirtualFileLocationHash);

      final String currentTimestamp = String.valueOf(new File(jarVirtualFile.getPath()).lastModified());
      final File timestampFile = new File(outputDir, ".idea.timestamp");

      String previousTimestamp = null;
      if (timestampFile.exists()) {
        previousTimestamp = FileUtil.loadFile(timestampFile);
      }

      if (!currentTimestamp.equals(previousTimestamp)) {
        final Ref<Boolean> extract = new Ref<Boolean>();
        Runnable r = new Runnable() {
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
        catch (InvocationTargetException e) {
          extract.set(false);
        }
        catch (InterruptedException e) {
          extract.set(false);
        }

        if (!extract.get()) return null;

        @SuppressWarnings("ConstantConditions")
        final ZipFile zipFile = jarFileSystem.getJarFile(file).getZipFile();
        if (zipFile == null) return null;
        ZipEntry entry = zipFile.getEntry(targetFileRelativePath);
        if (entry == null) return null;
        InputStream is = zipFile.getInputStream(entry);
        try {
          ZipUtil.extractEntry(entry, is, outputDir);
        }
        finally {
          is.close();
        }

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            new Task.Backgroundable(null, "Extracting files...", true) {
              public void run(@NotNull final ProgressIndicator indicator) {
                final int size = zipFile.size();
                final int[] counter = new int[]{0};

                class MyFilter implements FilenameFilter {
                  private final Set<File> myImportantDirs = new HashSet<File>(
                    Arrays.asList(outputDir, new File(outputDir, "resources")));
                  private final boolean myImportantOnly;

                  private MyFilter(boolean importantOnly) {
                    myImportantOnly = importantOnly;
                  }

                  public boolean accept(File dir, String name) {
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
                  ZipUtil.extract(zipFile, outputDir, new MyFilter(true));
                  ZipUtil.extract(zipFile, outputDir, new MyFilter(false));
                  FileUtil.writeToFile(timestampFile, currentTimestamp.getBytes());
                }
                catch (IOException ignore) {
                }
              }
            }.queue();
          }
        });
      }

      return VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(new File(outputDir, targetFileRelativePath).getPath())) + anchor;
    }
    catch (IOException e) {
      LOG.warn(e);
      Messages.showErrorDialog("Cannot extract files: " + e.getMessage(), "Error");
      return null;
    }
  }

  public static void clearExtractedFiles() {
    FileUtil.delete(getExtractedFilesDir());
  }

  private static File getExtractedFilesDir() {
    return new File(PathManager.getSystemPath(), "ExtractedFiles");
  }

  public static boolean isOpenCommandSupportArgs() {
    return SystemInfo.isMacOSSnowLeopard;
  }

  private static class ConfirmExtractDialog extends OptionsDialog {
    private ConfirmExtractDialog() {
      super(null);
      setTitle("Confirmation");
      init();
    }

    protected boolean isToBeShown() {
      return getGeneralSettingsInstance().isConfirmExtractFiles();
    }

    protected void setToBeShown(boolean value, boolean onOk) {
      getGeneralSettingsInstance().setConfirmExtractFiles(value);
    }

    protected boolean shouldSaveOptionsOnCancel() {
      return true;
    }

    protected Action[] createActions() {
      setOKButtonText(CommonBundle.getYesButtonText());
      return new Action[]{getOKAction(), getCancelAction()};
    }

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
}
