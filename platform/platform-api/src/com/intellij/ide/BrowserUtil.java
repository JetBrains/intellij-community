/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.Patches;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
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
import com.intellij.util.io.ZipUtil;
import com.intellij.util.ui.OptionsDialog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.intellij.util.containers.ContainerUtil.newSmartList;
import static com.intellij.util.containers.ContainerUtilRt.newArrayList;

public class BrowserUtil {
  private static final Logger LOG = Logger.getInstance(BrowserUtil.class);

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
    return anchorMatcher.find() ? anchorMatcher.reset().replaceAll("") : url;
  }

  @Nullable
  public static URL getURL(String url) throws MalformedURLException {
    return isAbsoluteURL(url) ? VfsUtil.convertToURL(url) : new URL("file", "", url);
  }

  public static void browse(@NotNull VirtualFile file) {
    browse(VfsUtil.toUri(file));
  }

  public static void browse(@NotNull File file) {
    browse(VfsUtil.toUri(file));
  }

  public static void browse(@NotNull URL url) {
    browse(url.toExternalForm());
  }

  public static void launchBrowser(@NotNull @NonNls String url) {
    browse(url);
  }

  public static void browse(@NotNull @NonNls String url) {
    openOrBrowse(url, true);
  }

  public static void open(@NotNull @NonNls String url) {
    openOrBrowse(url, false);
  }

  private static void openOrBrowse(@NotNull @NonNls String url, boolean browse) {
    url = url.trim();

    if (url.startsWith("jar:")) {
      String files = extractFiles(url);
      if (files == null) {
        return;
      }
      url = files;
    }

    URI uri;
    if (isAbsoluteURL(url)) {
      uri = VfsUtil.toUri(url);
    }
    else {
      File file = new File(url);
      if (!browse && isDesktopActionSupported(Desktop.Action.OPEN)) {
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
      showErrorMessage(IdeBundle.message("error.malformed.url", url), CommonBundle.getErrorTitle());
    }
    else {
      browse(uri);
    }
  }

  /**
   * Main method: tries to launch a browser using every possible way
   */
  public static void browse(@NotNull URI uri) {
    LOG.debug("Launch browser: [" + uri + "]");

    GeneralSettings settings = getGeneralSettingsInstance();
    if (settings.isUseDefaultBrowser()) {
      if (isDesktopActionSupported(Desktop.Action.BROWSE)) {
        try {
          Desktop.getDesktop().browse(uri);
          LOG.debug("Browser launched using JDK 1.6 API");
          return;
        }
        catch (Exception e) {
          LOG.warn("Error while using Desktop API, fallback to CLI", e);
        }
      }

      List<String> command = getDefaultBrowserCommand();
      if (command != null) {
        launchBrowserByCommand(uri, command);
        return;
      }
    }

    String browserPath = settings.getBrowserPath();
    if (StringUtil.isEmptyOrSpaces(browserPath)) {
      String message = IdeBundle.message("error.please.specify.path.to.web.browser", CommonBundle.settingsActionPath());
      showErrorMessage(message, IdeBundle.message("title.browser.not.found"));
      return;
    }

    launchBrowserByCommand(uri, getOpenBrowserCommand(browserPath));
  }

  private static boolean isDesktopActionSupported(Desktop.Action action) {
    return !Patches.SUN_BUG_ID_6457572 && !Patches.SUN_BUG_ID_6486393 &&
           Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(action);
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

  public static boolean canStartDefaultBrowser() {
    return isDesktopActionSupported(Desktop.Action.BROWSE) ||
           SystemInfo.isMac || SystemInfo.isWindows ||
           SystemInfo.isUnix && SystemInfo.hasXdgOpen();
  }

  @Nullable
  @NonNls
  private static List<String> getDefaultBrowserCommand() {
    if (SystemInfo.isWindows) {
      return newArrayList(ExecUtil.getWindowsShellName(), "/c", "start", GeneralCommandLine.inescapableQuote(""));
    }
    else if (SystemInfo.isMac) {
      return newSmartList(ExecUtil.getOpenCommandPath());
    }
    else if (SystemInfo.isUnix && SystemInfo.hasXdgOpen()) {
      return newSmartList("xdg-open");
    }

    return null;
  }

  private static void launchBrowserByCommand(@NotNull final URI uri, @NotNull final List<String> command) {
    try {
      GeneralCommandLine commandLine = new GeneralCommandLine(command);
      commandLine.addParameter(uri.toString());
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
  public static List<String> getOpenBrowserCommand(@NonNls @NotNull String browserPathOrName) {
    return getOpenBrowserCommand(browserPathOrName, false);
  }

  @NotNull
  public static List<String> getOpenBrowserCommand(@NonNls @NotNull String browserPathOrName, boolean newWindowIfPossible) {
    if (new File(browserPathOrName).isFile()) {
      return newSmartList(browserPathOrName);
    }
    else if (SystemInfo.isMac) {
      List<String> command = newArrayList(ExecUtil.getOpenCommandPath(), "-a", browserPathOrName);
      if (newWindowIfPossible) {
        command.add("-n");
      }
      return command;
    }
    else if (SystemInfo.isWindows) {
      return newArrayList(ExecUtil.getWindowsShellName(), "/c", "start", GeneralCommandLine.inescapableQuote(""), browserPathOrName);
    }
    else {
      return newSmartList(browserPathOrName);
    }
  }

  private static void showErrorMessage(final String message, final String title) {
    final Application app = ApplicationManager.getApplication();
    if (app == null) {
      return; // Not started yet. Not able to show message up. (Could happen in License panel under Linux).
    }

    Runnable runnable = new Runnable() {
      @Override
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
      LOG.assertTrue(targetFileRelativePath != null);

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
          @Override
          public void run() {
            new Task.Backgroundable(null, "Extracting files...", true) {
              @Override
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
}
