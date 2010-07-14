/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.GuiUtils;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.ZipUtil;
import com.intellij.util.ui.OptionsDialog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
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
  // local paths like "C:/temp/index.html" whould be erroneously interpreted as
  // external URLs.)
  @NonNls private static final Pattern ourExternalPrefix = Pattern.compile("^[\\w\\+\\.\\-]{2,}:");
  private static final Pattern ourAnchorsuffix = Pattern.compile("#(.*)$");

  private BrowserUtil() {
  }

  public static boolean isAbsoluteURL(String url) {
    return ourExternalPrefix.matcher(url.toLowerCase()).find();
  }

  public static String getDocURL(String url) {
    Matcher anchorMatcher = ourAnchorsuffix.matcher(url);

    if (anchorMatcher.find()) {
      return anchorMatcher.reset().replaceAll("");
    }

    return url;
  }

  public static URL getURL(String url) throws java.net.MalformedURLException {
    if (!isAbsoluteURL(url)) {
      return new URL("file", "", url);
    }

    return VfsUtil.convertToURL(url);
  }

  private static void launchBrowser(final String url, String[] command) {
    try {
      URL curl = getURL(url);

      if (curl != null) {
        final String urlString = curl.toString();
        String[] commandLine;
        if (SystemInfo.isWindows && isUseDefaultBrowser()) {
          commandLine = new String[command.length + 2];
          System.arraycopy(command, 0, commandLine, 0, command.length);
          commandLine[commandLine.length - 2] = "\"\"";
          commandLine[commandLine.length - 1] = "\"" + redirectUrl(url, urlString) + "\"";
        }
        else {
          commandLine = new String[command.length + 1];
          System.arraycopy(command, 0, commandLine, 0, command.length);
          commandLine[commandLine.length - 1] = escapeUrl(urlString);
        }
        Runtime.getRuntime().exec(commandLine);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Browser launched with command line: " + Arrays.toString(commandLine));
        }
      }
      else {
        showErrorMessage(IdeBundle.message("error.malformed.url", url), CommonBundle.getErrorTitle());
      }
    }
    catch (final IOException e) {
      showErrorMessage(IdeBundle.message("error.cannot.start.browser", e.getMessage()),
                       CommonBundle.getErrorTitle());
    }
  }

  /**
   * This method works around Windows 'start' command behaivor of dropping anchors from the url for local urls.
   */
  private static String redirectUrl(String url, @NonNls String urlString) throws IOException {
    if (url.indexOf('&') == -1 && (!urlString.startsWith("file:") || urlString.indexOf("#") == -1)) return urlString;

    File redirect = File.createTempFile("redirect", ".html");
    redirect.deleteOnExit();
    FileWriter writer = new FileWriter(redirect);
    writer.write("<html><head></head><body><script type=\"text/javascript\">window.location=\"" + url + "\";</script></body></html>");
    writer.close();
    return VfsUtil.pathToUrl(redirect.getAbsolutePath());
  }

  private static boolean isUseDefaultBrowser() {
    Application application = ApplicationManager.getApplication();
    if (application == null) {
      return true;
    }
    else {
      return getGeneralSettingsInstance().isUseDefaultBrowser();
    }
  }

  private static void showErrorMessage(final String message, final String title) {
    final Application app = ApplicationManager.getApplication();
    if (app == null) {
      return; // Not started yet. Not able to show message up. (Could happen in License panel under Linux).
    }

    Runnable runnable = new Runnable() {
      public void run() {
        Messages.showMessageDialog(message,
                                   title,
                                   Messages.getErrorIcon());
      }
    };

    if (app.isDispatchThread()) {
      runnable.run();
    }
    else {
      app.invokeLater(runnable, ModalityState.NON_MODAL);
    }
  }

  private static void launchBrowserUsingStandardWay(final String url) {
    String[] command;
    try {
      String browserPath = getGeneralSettingsInstance().getBrowserPath();
      if (browserPath == null || browserPath.trim().length() == 0) {
        showErrorMessage(IdeBundle.message("error.please.specify.path.to.web.browser"),
                         IdeBundle.message("title.browser.not.found"));
        return;
      }

      command = getOpenBrowserCommand(browserPath, url);
    }
    catch (NullPointerException e) {
      // todo: fix the possible problem on startup, see SCR #35066
      command = getDefaultBrowserCommand();
      if (command == null) {
        showErrorMessage(IdeBundle.message("error.please.open.url.manually", url, ApplicationNamesInfo.getInstance().getProductName()),
                         IdeBundle.message("title.browser.path.not.found"));
        return;
      }
    }
    // We do not need to check browserPath under Win32

    launchBrowser(url, command);
  }

  private static GeneralSettings getGeneralSettingsInstance() {
    final GeneralSettings settings = GeneralSettings.getInstance();
    if (settings != null) return settings;
    return new GeneralSettings();
  }

  private static boolean launchDefaultBrowserUsingJdk6Api(String sUrl) {
    try {
      Class desktopClass = BrowserUtil.class.getClassLoader().loadClass("java.awt.Desktop");
      Object desktop = desktopClass.getMethod("getDesktop").invoke(null);

      URL url = getURL(sUrl);

      if (url == null) return false;

      desktopClass.getMethod("browse", new Class[]{URI.class}).invoke(desktop, url.toURI());
      LOG.debug("Browser launched using JDK 1.6 API");
      return true;
    }
    catch (Exception e) {
      return false;
    }
  }

  @NotNull
  public static String escapeUrl(@NotNull @NonNls String url) {
    if (SystemInfo.isWindows) {
      return url.indexOf(' ') > 0? "\"" + url + "\"" : url;
    }
    else {
      return url.replaceAll(" ", "%20");
    }
  }

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
      String targetFileRelativePath = targetFilePath.substring(
        targetFilePath.indexOf(JarFileSystem.JAR_SEPARATOR) + JarFileSystem.JAR_SEPARATOR.length());

      String jarVirtualFileLocationHash = jarVirtualFile.getName() + Integer.toHexString(jarVirtualFile.getUrl().hashCode());
      final File outputDir = new File(getExtractedFilesDir(), jarVirtualFileLocationHash);

      final String currentTimestamp = String.valueOf(new File(jarVirtualFile.getPath()).lastModified());
      final File timestampFile = new File(outputDir, ".idea.timestamp");

      String previousTimestamp = null;
      if (timestampFile.exists()) {
        previousTimestamp = new String(FileUtil.loadFileText(timestampFile));
      }

      if (!currentTimestamp.equals(previousTimestamp)) {
        final Ref<Boolean> extract = new Ref<Boolean>();
        Runnable r = new Runnable() {
          public void run() {
            final ConfirmExtractDialog dialog = new ConfirmExtractDialog();
            if (dialog.isToBeShown()) {
              dialog.show();
              extract.set(dialog.isOK());
            } else {
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

  public static void launchBrowser(@NonNls String url) {
    LOG.debug("Launch browser: " + url);
    if (url.startsWith("jar:")) {
      url = extractFiles(url);
      if (url == null) return;
    }
    if (canStartDefaultBrowser() && isUseDefaultBrowser()) {
      if (launchDefaultBrowserUsingJdk6Api(url)) {
        return;
      }

      launchBrowser(url, getDefaultBrowserCommand());
    }
    else {
      launchBrowserUsingStandardWay(url);
    }
  }

  @NonNls
  private static String[] getDefaultBrowserCommand() {
    if (SystemInfo.isWindows9x) {
      return new String[]{"command.com", "/c", "start"};
    }
    else if (SystemInfo.isWindows) {
      return new String[]{"cmd.exe", "/c", "start"};
    }
    else if (SystemInfo.isMac) {
      return new String[]{"open"};
    }
    else if (SystemInfo.isUnix) {
      return new String[]{"mozilla"};
    }
    else {
      return null;
    }
  }

  public static boolean canStartDefaultBrowser() {
    if (SystemInfo.isMac) {
      return true;
    }

    if (SystemInfo.isWindows) {
      return true;
    }

    try {
      Class desktopClass = BrowserUtil.class.getClassLoader().loadClass("java.awt.Desktop");
      Object desktop = desktopClass.getMethod("getDesktop", ArrayUtil.EMPTY_CLASS_ARRAY).invoke(null);


      Class browseActionClass = BrowserUtil.class.getClassLoader().loadClass("java.awt.Desktop$Action");
      Object browseAction = browseActionClass.getField("BROWSE").get(null);

      Object res = desktopClass.getMethod("isSupported", new Class[]{browseActionClass}).invoke(desktop, browseAction);

      return (Boolean)res;
    }
    catch (Exception e) {
      return false;
    }
  }

  public static String[] getOpenBrowserCommand(final @NonNls @NotNull String browserPath, final String... parameters) {
    String[] command;
    if (SystemInfo.isMac) {
      if (parameters != null && parameters.length > 1) {
        //open -a command doesn't support additional parameters
        command = new String[] {browserPath};
      }
      else {
        command = new String[]{"open", "-a", browserPath};
      }
    }
    else if (SystemInfo.isWindows9x) {
      if (browserPath.indexOf(File.separatorChar) != -1) {
        command = new String[]{browserPath};
      }
      else {
        command = new String[]{"command.com", "/c", "start", browserPath};
      }
    }
    else if (SystemInfo.isWindows) {
      if (browserPath.indexOf(File.separatorChar) != -1) {
        command = new String[]{browserPath};
      }
      else {
        command = new String[]{"cmd.exe", "/c", "start", browserPath};
      }
    }
    else {
      command = new String[]{browserPath};
    }
    return command;
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
