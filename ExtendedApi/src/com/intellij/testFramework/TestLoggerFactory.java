package com.intellij.testFramework;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.log4j.LogManager;
import org.apache.log4j.xml.DOMConfigurator;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.StringReader;

@NonNls public class TestLoggerFactory implements Logger.Factory {
  private static final String SYSTEM_MACRO = "$SYSTEM_DIR$";
  private static final String APPLICATION_MACRO = "$APPLICATION_DIR$";
  private static final String LOGDIR_MACRO = "$LOG_DIR$";

  private boolean myInitialized = false;

  private static final TestLoggerFactory ourInstance = new TestLoggerFactory();
  public static final String LOG_DIR = "testlog";

  public static TestLoggerFactory getInstance() {
    return ourInstance;
  }

  private TestLoggerFactory() {
  }

  public Logger getLoggerInstance(String name) {
    synchronized (this) {
      try {
        if (!isInitialized()) {
          init();
        }
      }
      catch (Exception e) {
        e.printStackTrace();
      }

      return new TestLogger(org.apache.log4j.Logger.getLogger(name));
    }
  }

  private void init() {
    try {
      String fileName = PathManager.getBinPath() + File.separator + "log.xml";
      File logXmlFile = new File(fileName);
      if (!logXmlFile.exists()) {
        return;
      }
      System.setProperty("log4j.defaultInitOverride", "true");
      String text = new String(FileUtil.loadFileText(logXmlFile));
      text = StringUtil.replace(text, SYSTEM_MACRO, StringUtil.replace(PathManager.getSystemPath(), "\\", "\\\\"));
      text = StringUtil.replace(text, APPLICATION_MACRO, StringUtil.replace(PathManager.getHomePath(), "\\", "\\\\"));
      text = StringUtil.replace(text, LOGDIR_MACRO, StringUtil.replace(LOG_DIR, "\\", "\\\\"));

      File file = new File(PathManager.getSystemPath() + File.separator + LOG_DIR);
      file.mkdirs();

      DOMConfigurator domConfigurator = new DOMConfigurator();
      try {
        domConfigurator.doConfigure(new StringReader(text), LogManager.getLoggerRepository());
      }
      catch (ClassCastException e) {
        // shit :-E
        System.out.println("log.xml content:\n" + text);
        throw e;
      }
      myInitialized = true;
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private boolean isInitialized() {
    return myInitialized;
  }


}