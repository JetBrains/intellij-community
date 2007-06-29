/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BrowserUtil {

  // The pattern for 'scheme' mainly according to RFC1738.
  // We have to violate the RFC since we need to distinguish
  // real schemes from local Windows paths; The only difference
  // with RFC is that we do not allow schemes with length=1 (in other case
  // local paths like "C:/temp/index.html" whould be erroneously interpreted as
  // external URLs.)
  @NonNls private static Pattern ourExternalPrefix = Pattern.compile("^[\\w\\+\\.\\-]{2,}:");
  private static Pattern ourAnchorsuffix = Pattern.compile("#(.*)$");

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
      URL curl = BrowserUtil.getURL(url);

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
          if (SystemInfo.isWindows) {
            commandLine[commandLine.length - 1] = "\"" + urlString + "\"";
          }
          else {
            commandLine[commandLine.length - 1] = urlString.replaceAll(" ", "%20");
          }
        }
        Runtime.getRuntime().exec(commandLine);
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
      return GeneralSettings.getInstance().isUseDefaultBrowser();
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
      String browserPath = GeneralSettings.getInstance().getBrowserPath();
      if (browserPath == null || browserPath.trim().length() == 0) {
        showErrorMessage(IdeBundle.message("error.please.specify.path.to.web.browser"),
                         IdeBundle.message("title.browser.not.found"));
        return;
      }

      command = new String[]{browserPath};
    }
    catch (NullPointerException e) {
      // todo: fix the possible problem on startup, see SCR #35066
      command = getDefaultBrowserCommand(null);
      if (command == null) {
        showErrorMessage(IdeBundle.message("error.please.open.url.manually", url, ApplicationNamesInfo.getInstance().getProductName()),
                         IdeBundle.message("title.browser.path.not.found"));
        return;
      }

    }
    // We do not need to check browserPath under Win32

    launchBrowser(url, command);
  }


  public static void launchBrowser(final String url, String name) {
    //noinspection HardCodedStringLiteral
    if (url.startsWith("jar:")) {
      showErrorMessage(IdeBundle.message("error.cannot.show.in.external.browser", url),
                       IdeBundle.message("title.cannot.start.browser"));
      return;
    }
    if (canStartDefaultBrowser() && isUseDefaultBrowser()) {
      launchBrowser(url, getDefaultBrowserCommand(name));
    }
    else {
      launchBrowserUsingStandardWay(url);
    }
  }

  public static void launchBrowser(@NonNls final String url) {
    launchBrowser(url, (String)null);
  }

  @NonNls private static String[] getDefaultBrowserCommand(String name) {
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

    return false;
  }
}
