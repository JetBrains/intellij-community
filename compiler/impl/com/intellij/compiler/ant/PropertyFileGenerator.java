/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler.ant;

import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Generator for property files.
 *
 * @author Eugene Zhuravlev
 *         Date: Nov 27, 2004
 */
public class PropertyFileGenerator extends Generator{
  /**
   * List of the properties
   */
  private List<Pair<String, String>> myProperties = new ArrayList<Pair<String, String>>();

  /**
   * A constctor that extracts all neeed properties for ant build from the project.
   *
   * @param project a project to examine
   * @param genOptions generation options
   */
  public PropertyFileGenerator(Project project, GenerationOptions genOptions) {
    // path variables
    final PathMacros pathMacros = PathMacros.getInstance();
    final Set<String> macroNamesSet = pathMacros.getUserMacroNames();
    if (macroNamesSet.size() > 0) {
      final String[] macroNames = macroNamesSet.toArray(new String[macroNamesSet.size()]);
      Arrays.sort(macroNames);
      for (final String macroName : macroNames) {
        addProperty(BuildProperties.getPathMacroProperty(macroName), pathMacros.getValue(macroName));
      }
    }
    // jdk homes
    if (genOptions.forceTargetJdk) {
      final Sdk[] usedJdks = BuildProperties.getUsedJdks(project);
      for (Sdk jdk : usedJdks) {
        if (jdk.getHomeDirectory() == null) {
          continue;
        }
        final File homeDir = BuildProperties.toCanonicalFile(VfsUtil.virtualToIoFile(jdk.getHomeDirectory()));
        addProperty(BuildProperties.getJdkHomeProperty(jdk.getName()), homeDir.getPath().replace(File.separatorChar, '/'));
      }
    }
  }

  /**
   * Add property. Note that property name and value
   * are automatically escaped when the property file
   * is generated.
   *
   * @param name a property name
   * @param value a property value
   */
  public void addProperty(String name, String value) {
    myProperties.add(new Pair<String, String>(name, value));
  }

  @Override
  public void generate(PrintWriter out) throws IOException {
    boolean isFirst = true;
    for (final Pair<String, String> pair : myProperties) {
      if (!isFirst) {
        crlf(out);
      }
      else {
        isFirst = false;
      }
      out.print(StringUtil.escapeProperty(pair.getFirst(), true));
      out.print("=");
      out.print(StringUtil.escapeProperty(pair.getSecond(), false));
    }
  }
}
