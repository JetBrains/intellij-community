package org.jetbrains.jps.gwt;

import groovy.util.XmlParser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class GwtModulesSearcher {
  private static final String GWT_XML_SUFFIX = ".gwt.xml";

  public static List<String> findGwtModules(List<? extends CharSequence> sourcePaths) {
    final List<String> result = new ArrayList<String>();
    for (CharSequence sourcePath : sourcePaths) {
      collectGwtModules(new File(sourcePath.toString()), "", result);
    }
    return result;
  }

  private static void collectGwtModules(File dir, String packageName, List<String> result) {
    if (!dir.isDirectory()) {
      return;
    }

    final File[] files = dir.listFiles();
    if (files != null) {
      for (File child : files) {
        final String name = child.getName();
        if (child.isFile() && name.endsWith(GWT_XML_SUFFIX)) {
          if (GwtModuleUtil.hasEntryPoints(child)) {
            result.add(packageName + name.substring(0, name.length() - GWT_XML_SUFFIX.length()));
          }
        }
        else {
          collectGwtModules(child, packageName + name + ".", result);
        }
      }
    }
  }
}
