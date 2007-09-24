/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler.ant;

import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;

import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 27, 2004
 */
public class PropertyFileGenerator extends Generator{
  private List<Pair<String, String>> myProperties = new ArrayList<Pair<String, String>>();

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
      final ProjectJdk[] usedJdks = BuildProperties.getUsedJdks(project);
      for (ProjectJdk jdk : usedJdks) {
        if (jdk.getHomeDirectory() == null) {
          continue;
        }
        final File homeDir = BuildProperties.toCanonicalFile(VfsUtil.virtualToIoFile(jdk.getHomeDirectory()));
        addProperty(BuildProperties.getJdkHomeProperty(jdk.getName()), homeDir.getPath().replace(File.separatorChar, '/'));
      }
    }
  }

  public void addProperty(String name, String value) {
    myProperties.add(new Pair<String, String>(name, value));
  }

  public void generate(DataOutput out) throws IOException {
    boolean isFirst = true;
    for (final Pair<String, String> myProperty : myProperties) {
      final Pair<String, String> pair = (Pair<String, String>)myProperty;
      if (!isFirst) {
        crlf(out);
      }
      else {
        isFirst = false;
      }
      out.writeBytes(pair.getFirst());
      out.writeBytes("=");
      out.writeBytes(pair.getSecond());
    }
  }
}
