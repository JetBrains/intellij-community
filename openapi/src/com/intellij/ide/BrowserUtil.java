package com.intellij.ide;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtil;

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
  private static Pattern ourExternalPrefix = Pattern.compile("^[\\w\\+\\.\\-]{2,}:");
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
          commandLine[commandLine.length - 1] = "\"" + urlString + "\"";
        }
        else {
          commandLine = new String[command.length + 1];
          System.arraycopy(command, 0, commandLine, 0, command.length);
          commandLine[commandLine.length - 1] = urlString;
        }
        Runtime.getRuntime().exec(commandLine);
      }
      else {
        showErrorMessage("Malformed url: " + url, "Error");
      }
    }
    catch (final IOException e) {
      showErrorMessage("Cannot start browser: " + e.getMessage(), "Error");
    }
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
    Runnable runnable = new Runnable() {
      public void run() {
        Messages.showMessageDialog(message,
                                   title,
                                   Messages.getErrorIcon());
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(runnable, ModalityState.NON_MMODAL);
    }
  }

  private static void launchBrowserUsingStandardWay(final String url) {
    String[] command;
    try {
      String browserPath = GeneralSettings.getInstance().getBrowserPath();
      if (browserPath == null || browserPath.trim().length() == 0) {
        showErrorMessage("Please specify a path to web browser in File | Settings | General", "Browser Not Found");
        return;
      }
      else if (url.startsWith("jar:")) {
        showErrorMessage("Cannot show \"" + url + "\" in external browser", "Cannot start browser");
        return;
      }

      command = new String[]{browserPath};
    }
    catch (NullPointerException e) {
      // todo: fix the possible problem on startup, see SCR #35066
      command = getDefaultBrowserCommand(null);
      if (command == null) {
        showErrorMessage("Please open URL (" + url + ") manualy. IDEA can't open it in browser", "Browser Path Not Found");
        return;
      }

    }
    // We do not need to check browserPath under Win32

    launchBrowser(url, command);
  }


  public static void launchBrowser(final String url, String name) {
    if (canStartDefaultBrowser() && isUseDefaultBrowser()) {
      launchBrowser(url, getDefaultBrowserCommand(name));
    }
    else {
      launchBrowserUsingStandardWay(url);
    }
  }

  public static void launchBrowser(final String url) {
    launchBrowser(url, (String)null);
  }

  private static String[] getDefaultBrowserCommand(String name) {
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
