/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.application;

import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.text.StringUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.PropertyResourceBundle;
import java.util.StringTokenizer;
import java.util.MissingResourceException;

public class PathManager {
  private static final String PROPERTY_SYSTEM_PATH = "idea.system.path";
  private static final String PROPERTY_CONFIG_PATH = "idea.config.path";
  private static final String PROPERTY_PLUGINS_PATH = "idea.plugins.path";
  private static final String PROPERTY_HOME_PATH = "idea.home.path";

  private static String ourHomePath;
  private static String ourSystemPath;
  private static String ourConfigPath;
  private static String ourPluginsPath;
  private static String ourPreinstalledPluginsPath;

  private static final String FILE = "file";
  private static final String JAR = "jar";
  private static final String JAR_DELIMITER = "!";
  private static final String PROTOCOL_DELIMITER = ":";
  public static final String DEFAULT_OPTIONS_FILE_NAME = "other";

  public static String getHomePath() {
    if (ourHomePath != null) return ourHomePath;

    if (System.getProperty(PROPERTY_HOME_PATH) != null) {
      ourHomePath = getAbsolutePath(System.getProperty(PROPERTY_HOME_PATH));
      return ourHomePath;
    }

    final Class aClass = PathManager.class;

    String rootPath = getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    if (rootPath != null) {
      File root = new File(rootPath);
      root = root.getAbsoluteFile();

      root = new File(root.getParent()); // one step back to get folder
/*
      if (!root.isDirectory() || root.getName().toLowerCase().endsWith(".zip") || root.getName().toLowerCase().endsWith(".jar")) {
        root = new File(root.getParent()); // one step back to get folder
      }
*/
      root = root.getAbsoluteFile();

      ourHomePath = root.getParentFile().getAbsolutePath();    // one step back to get rid of "lib" or "classes" folder
    }

    return ourHomePath;
  }

  public static String getLibPath() {
    return getHomePath() + File.separator + "lib";
  }

  private static String trimPathQuotes(String path){
    if (!(path != null && !(path.length() < 3))){
      return path;
    }
    if (StringUtil.startsWithChar(path, '\"') && StringUtil.endsWithChar(path, '\"')){
      return path.substring(1, path.length() - 1);
    }
    return path;
  }

  public static String getSystemPath() {
    if (ourSystemPath != null) return ourSystemPath;

    if (System.getProperty(PROPERTY_SYSTEM_PATH) != null) {
      ourSystemPath = getAbsolutePath(trimPathQuotes(System.getProperty(PROPERTY_SYSTEM_PATH)));
    }
    else {
      if (PropertyLoader.getProperty(PROPERTY_SYSTEM_PATH) != null)
        ourSystemPath = getAbsolutePath(trimPathQuotes(PropertyLoader.getProperty(PROPERTY_SYSTEM_PATH)));
      else
        ourSystemPath = getHomePath() + File.separator + "system";
    }

    try {
      File file = new File(ourSystemPath);
      file.mkdirs();
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    return ourSystemPath;
  }

  public static void ensureConfigFolderExists(boolean userInteractionAllowed) {
    getConfigPathWithoutDialog();

    File file = new File(ourConfigPath);
    if (!file.exists()) {
      file.mkdirs();
      if (userInteractionAllowed) {
        ConfigImportHelper.importConfigsTo(ourConfigPath);
      }
    }
  }

  public static String getConfigPath() {
    ensureConfigFolderExists(false);
    return ourConfigPath;
  }

  private static String  getConfigPathWithoutDialog() {
    if (ourConfigPath != null) return ourConfigPath;

    if (System.getProperty(PROPERTY_CONFIG_PATH) != null) {
      ourConfigPath = getAbsolutePath(trimPathQuotes(System.getProperty(PROPERTY_CONFIG_PATH)));
    }
    else {
      if (PropertyLoader.getProperty(PROPERTY_CONFIG_PATH) != null)
        ourConfigPath = getAbsolutePath(trimPathQuotes(PropertyLoader.getProperty(PROPERTY_CONFIG_PATH)));
      else
      ourConfigPath = getHomePath() + File.separator + "config";
    }
    return ourConfigPath;
  }

  public static String getHelpURL() {
    return "jar:file:///" + getHelpJarPath() + "!/idea";
  }

  private static String getHelpJarPath() {
    return getHomePath() + File.separator + "help" + File.separator + "ideahelp.jar";
  }

  public static File getHelpJarFile() {
    return new File(getHelpJarPath());
  }

  public static String getBinPath() {
    return getHomePath() + File.separator + "bin";
  }

  public static String getOptionsPath() {
    return getConfigPath() + File.separator + "options";
  }

  public static String getOptionsPathWithoutDialog() {
    return getConfigPathWithoutDialog() + File.separator + "options";
  }

  public static String getPreinstalledPluginsPath() {
    if (ourPreinstalledPluginsPath == null) {
      ourPreinstalledPluginsPath = getHomePath() + File.separatorChar + "plugins";
    }

    return ourPreinstalledPluginsPath;
  }

  public static String getPluginsPath() {
    if (ourPluginsPath == null) {
      if (System.getProperty(PROPERTY_PLUGINS_PATH) != null) {
        ourPluginsPath = getAbsolutePath(trimPathQuotes(System.getProperty(PROPERTY_PLUGINS_PATH)));
      } else {
        if (PropertyLoader.getProperty(PROPERTY_PLUGINS_PATH) != null)
          ourPluginsPath = getAbsolutePath(trimPathQuotes(PropertyLoader.getProperty(PROPERTY_PLUGINS_PATH)));
        else
        ourPluginsPath = getConfigPath() + File.separatorChar + "plugins";
      }
    }

    return ourPluginsPath;
  }

  private static String getAbsolutePath(String path) {
    if (path.startsWith("~/") || path.startsWith("~\\")) {
      path = System.getProperty("user.home") + path.substring(1);
    }

    File file = new File(path);
    if (!file.exists()) return path;
    file = file.getAbsoluteFile();
    return file.getAbsolutePath();
  }

  public static File getOptionsFile(NamedJDOMExternalizable externalizable) {
    return new File(getOptionsPath()+File.separatorChar+externalizable.getExternalFileName()+".xml");
  }

  /**
   * Attempts to detect classpath entry which contains given resource
   */
  public static String getResourceRoot(Class context, String path) {
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
    if (!(StringUtil.startsWithChar(resourcePath, '/') || StringUtil.startsWithChar(resourcePath, '\\'))) {
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

    resultPath = StringUtil.replace(resultPath, "%20", " ");
    resultPath = StringUtil.replace(resultPath, "%23", "#");

    return resultPath;
  }

  public static File getDefaultOptionsFile() {
    return new File(getOptionsPath(),DEFAULT_OPTIONS_FILE_NAME+".xml");
  }

  private static class PropertyLoader {
    private static final String IDEA_PROPERTIES = "idea.properties";
    private static ResourceBundle ourBundle;

    private static final String LOAD_THIS_PROPERTIES = "load.this.properties";

    private PropertyLoader() {
    }


    static {
      String loadThis = getProperty(LOAD_THIS_PROPERTIES);
      if (loadThis != null) {
        for (StringTokenizer stringTokenizer = new StringTokenizer(loadThis, ";"); stringTokenizer.hasMoreTokens();) {
          String property = stringTokenizer.nextToken();
          StringTokenizer tokenizer = new StringTokenizer(property, "=");

          String propertyName = null;
          String propertyValue = null;

          if (tokenizer.hasMoreTokens()) {
            propertyName = tokenizer.nextToken();
            if (tokenizer.hasMoreTokens()) {
              propertyValue = tokenizer.nextToken();
            }
          }

          if (propertyName != null && propertyValue != null) {
            System.setProperty(propertyName, propertyValue);
          }
        }
      }
    }

    public static String getProperty (String key) {
      if (ourBundle == null) {
        init();
      }

      try {
        if (ourBundle == null)
          return null;
        else
          return ourBundle.getString(key);
      }
      catch (MissingResourceException e) {
        // it's not a problem.
        return null;
      }
    }

    private static void init() {
      File ideaPropertiesFile = new File(IDEA_PROPERTIES);
      if (ideaPropertiesFile.exists()) {
        try {
          FileInputStream fis = new FileInputStream(IDEA_PROPERTIES);
          ourBundle = new PropertyResourceBundle (fis);
          fis.close();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

}
