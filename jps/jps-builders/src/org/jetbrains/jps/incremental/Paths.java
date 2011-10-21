package org.jetbrains.jps.incremental;

import java.io.File;
import java.util.Locale;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/20/11
 */
public class Paths {
  private static final Paths ourInstance = new Paths();
  private volatile File mySystemRoot = new File(System.getProperty("user.home", ".jps-server"));

  private Paths() {
  }

  public static Paths getInstance() {
    return ourInstance;
  }

  public File getSystemRoot() {
    return mySystemRoot;
  }

  public void setSystemRoot(File systemRoot) {
    mySystemRoot = systemRoot;
  }

  public static File getDataStorageRoot(String projectName) {
    return new File(getInstance().mySystemRoot, projectName.toLowerCase(Locale.US));
  }

  public static File getBuilderDataRoot(final String projectName, String builderName) {
    return new File(getDataStorageRoot(projectName), builderName.toLowerCase(Locale.US));
  }

  public static File getMappingsStorageFile(final String projectName) {
    return new File(getDataStorageRoot(projectName), "mappings/data");
  }
}
