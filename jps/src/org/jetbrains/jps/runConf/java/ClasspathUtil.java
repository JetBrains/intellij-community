package org.jetbrains.jps.runConf.java;

import java.io.File;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Set;

public class ClasspathUtil {
  private static final String FILE = "file";
  private static final String JAR = "jar";
  private static final String JAR_DELIMITER = "!";
  private static final String PROTOCOL_DELIMITER = ":";

  public static String composeClasspath(Class[] classes) {
    Set<String> cp = new LinkedHashSet<String>();
    if (classes != null) {
      for (Class classFromLib : classes) {
        cp.add(getResourceRoot(classFromLib));
      }
    }

    StringBuilder result = new StringBuilder();
    for (String path : cp) {
      if (result.length() > 0) {
        result.append(File.pathSeparator);
      }
      result.append(path);
    }

    return result.toString();
  }

  /**
   * Attempts to detect classpath entry which contains given resource
   */
  private static String getResourceRoot(Class context, String path) {
    URL url = context.getResource(path);
    if (url == null) {
      url = ClassLoader.getSystemResource(path.substring(1));
    }
    if (url == null) {
      return null;
    }
    return extractRoot(url, path);
  }

  /**
   * Attempts to extract classpath entry part from passed URL.
   */
  private static String extractRoot(URL resourceURL, String resourcePath) {
    if (!(resourcePath.startsWith("/") || resourcePath.startsWith("\\"))) {
      //noinspection HardCodedStringLiteral
      System.err.println("precondition failed");
      return null;
    }
    String protocol = resourceURL.getProtocol();
    String resultPath = null;

    if (FILE.equals(protocol)) {
      String path = resourceURL.getFile();
      String testPath = path.replace('\\', '/').toLowerCase();
      String testResourcePath = resourcePath.replace('\\', '/').toLowerCase();
      if (testPath.endsWith(testResourcePath)) {
        resultPath = path.substring(0, path.length() - resourcePath.length());
      }
    }
    else if (JAR.equals(protocol)) {
      String fullPath = resourceURL.getFile();
      int delimiter = fullPath.indexOf(JAR_DELIMITER);
      if (delimiter >= 0) {
        String archivePath = fullPath.substring(0, delimiter);
        if (archivePath.startsWith(FILE + PROTOCOL_DELIMITER)) {
          resultPath = archivePath.substring(FILE.length() + PROTOCOL_DELIMITER.length());
        }
      }
    }

    if (resultPath != null && resultPath.endsWith(File.separator)) {
      resultPath = resultPath.substring(0, resultPath.length() - 1);
    }

    resultPath = replaceAll(resultPath, "%20", " ");
    resultPath = replaceAll(resultPath, "%23", "#");

    // !Workaround for /D:/some/path/a.jar, which doesn't work if D is subst disk
    if (resultPath.startsWith("/") && resultPath.indexOf(":") == 2) {
      resultPath = resultPath.substring(1);
    }

    return resultPath;
  }

  private static String replaceAll(final String text, final String pattern, final String replacement) {
    if (pattern.length() == 0 || text.length() < pattern.length()) {
      return text;
    }
    final StringBuilder buf = new StringBuilder(text.length());
    int currentTextIndex = 0;
    while (currentTextIndex < text.length()) {
      final int startOfPattern = text.indexOf(pattern, currentTextIndex);
      if (startOfPattern < 0) {
        if (currentTextIndex == 0) { // there are no patterns in the text at all
          return text;
        }
        buf.append(text.substring(currentTextIndex)); // append the rest of the text
        return buf.toString();
      }
      buf.append(text.substring(currentTextIndex, startOfPattern));
      buf.append(replacement);
      currentTextIndex = startOfPattern + pattern.length();
    }
    return buf.toString();
  }

  private static String getResourceRoot(final Class aClass) {
    return getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
  }
}
