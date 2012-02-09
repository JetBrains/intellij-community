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
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.GuiUtils;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class BrowserUtil {
  private static final Logger LOG = Logger.getInstance("#" + BrowserUtil.class.getName());

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
  public static void launchBrowser(@NonNls String url) {
    LOG.debug("Launch browser: " + url);

    if (url.startsWith("jar:")) {
      url = extractFiles(url);
      if (url == null) return;
    }

    if (getGeneralSettingsInstance().isUseDefaultBrowser() && canStartDefaultBrowser()) {
      final String[] command = getDefaultBrowserCommand();
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
    else if (SystemInfo.isUnix && SystemInfo.hasXdgOpen) {
      return true;
    }
    else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
      return true;
    }

    return false;
  }

  @Nullable
  @NonNls
  private static String[] getDefaultBrowserCommand() {
    if (SystemInfo.isWindows9x) {
      return new String[]{"command.com", "/c", "start"};
    }
    else if (SystemInfo.isWindows) {
      return new String[]{"cmd.exe", "/c", "start"};
    }
    else if (SystemInfo.isMac) {
      return new String[]{ExecUtil.getOpenCommandPath()};
    }
    else if (SystemInfo.isUnix && SystemInfo.hasXdgOpen) {
      return new String[]{"xdg-open"};
    }

    return null;
  }

  private static void launchBrowserByCommand(final String url, @NotNull final String[] command) {
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
      commandLine.addParameter(curl.toString());
      commandLine.createProcess();

      if (LOG.isDebugEnabled()) {
        LOG.debug("Browser launched with command line: " + commandLine.getCommandLineString());
      }
    }
    catch (final ExecutionException e) {
      showErrorMessage(IdeBundle.message("error.cannot.start.browser", e.getMessage()), CommonBundle.getErrorTitle());
    }
  }

  @NotNull
  public static String escapeUrl(@NotNull @NonNls String url) {
    if (SystemInfo.isWindows) {
      return (url.indexOf(' ') > 0 || url.indexOf('&') > -1) ? "\"" + url + "\"" : url;
    }
    else {
      return url.replaceAll(" ", "%20");
    }
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
    String browserPath = getGeneralSettingsInstance().getBrowserPath();
    if (StringUtil.isEmptyOrSpaces(browserPath)) {
      showErrorMessage(IdeBundle.message("error.please.specify.path.to.web.browser"), IdeBundle.message("title.browser.not.found"));
      return;
    }

    launchBrowserByCommand(url, getOpenBrowserCommand(browserPath));
  }

  /**
   * @deprecated use {@link #getOpenBrowserCommand(String)} instead
   */
  @SuppressWarnings({"UnusedDeclaration"})
  public static String[] getOpenBrowserCommand(final @NonNls @NotNull String browserPath, final String... parameters) {
    return getOpenBrowserCommand(browserPath);
  }

  public static String[] getOpenBrowserCommand(final @NonNls @NotNull String browserPath) {
    final String[] command;
    if (SystemInfo.isMac) {
      if (new File(browserPath).isFile()) {
        // versions before 10.6 don't allow to pass command line arguments to browser via 'open' command
        // so we use full path to browser executable in such case
        command = new String[] {browserPath};
      }
      else {
        command = new String[]{ExecUtil.getOpenCommandPath(), "-a", browserPath};
      }
    }
    else if (SystemInfo.isWindows) {
      if (new File(browserPath).isFile()) {
        command = new String[]{browserPath};
      }
      else {
        command = new String[]{SystemInfo.isWindows9x ? "command.com" : "cmd.exe", "/c", "start", browserPath};
      }
    }
    else {
      command = new String[]{browserPath};
    }
    return command;
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

        final ZipFile jarFile = jarFileSystem.getJarFile(file);
        ZipEntry entry = jarFile.getEntry(targetFileRelativePath);
        if (entry == null) return null;
        InputStream is = jarFile.getInputStream(entry);
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
                final int size = jarFile.size();
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
                  ZipUtil.extract(jarFile, outputDir, new MyFilter(true));
                  ZipUtil.extract(jarFile, outputDir, new MyFilter(false));
                  FileUtil.writeToFile(timestampFile, currentTimestamp.getBytes());
                }
                catch (IOException ignore) {
                }
              }
            }.queue();
          }
        });
      }

      return VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(new File(outputDir, targetFileRelativePath).getPath())) + anchor;
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
