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
package com.intellij.openapi.application;

import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.text.StringUtil;

import java.io.*;
import java.net.URL;
import java.util.*;

public class PathManager {
  private static final String PROPERTIES_FILE = "idea.properties.file";
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

  public static void ensureConfigFolderExists(boolean userInteractionAllowed, final boolean createIfNotExists) {
    getConfigPathWithoutDialog();

    File file = new File(ourConfigPath);
    if (createIfNotExists && !file.exists()) {
      file.mkdirs();
      if (userInteractionAllowed) {
        ConfigImportHelper.importConfigsTo(ourConfigPath);
      }
    }
  }

  public static String getConfigPath(boolean createIfNotExists) {
    ensureConfigFolderExists(false, createIfNotExists);
    return ourConfigPath;
  }

  public static String getConfigPath() {
    return getConfigPath(true);
  }

  private static String  getConfigPathWithoutDialog() {
    if (ourConfigPath != null) return ourConfigPath;

    if (System.getProperty(PROPERTY_CONFIG_PATH) != null) {
      ourConfigPath = getAbsolutePath(trimPathQuotes(System.getProperty(PROPERTY_CONFIG_PATH)));
    }
    else {
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

  public static void loadProperties() {
    String propFilePath = System.getProperty(PROPERTIES_FILE);
    if (StringUtil.isEmptyOrSpaces(propFilePath) || !new File(propFilePath).exists()) {
      propFilePath = System.getProperty("user.home") + File.separator + "idea.properties";
      if (StringUtil.isEmptyOrSpaces(propFilePath) || !new File(propFilePath).exists()) {
        propFilePath = getBinPath() + File.separator + "idea.properties";
      }
    }

    File propFile = new File(propFilePath);
    if (propFile.exists()) {
      try {
        final InputStream fis = new BufferedInputStream(new FileInputStream(propFile));
        final PropertyResourceBundle bundle = new PropertyResourceBundle(fis);
        fis.close();
        final Enumeration keys = bundle.getKeys();
        final Properties sysProperties = System.getProperties();
        while (keys.hasMoreElements()) {
          String key = (String)keys.nextElement();
          final String value = substitueVars(bundle.getString(key));
          if (sysProperties.getProperty(key, null) == null) { // load the property from the property file only if it is not defined yet
            sysProperties.setProperty(key, value);
          }
        }
      }
      catch (IOException e) {
        System.out.println("Problem reading from property file: " + propFilePath);
      }
    }
  }

  public static String substitueVars(String s) {
    final String ideaHomePath = PathManager.getHomePath();
    return substituteVars(s, ideaHomePath);
  }

  public static String substituteVars(String s, final String ideaHomePath) {
    if (s.startsWith("..")) s = ideaHomePath + File.separatorChar + "bin" + File.separatorChar + s;
    s = StringUtil.replace(s, "${idea.home}", ideaHomePath);
    final Properties props = System.getProperties();
    final Set keys = props.keySet();
    for (Iterator iterator = keys.iterator(); iterator.hasNext();) {
      String key = (String)iterator.next();
      String value = props.getProperty(key);
      s = StringUtil.replace(s, "${" + key + "}", value);
    }
    return s;
  }
}
