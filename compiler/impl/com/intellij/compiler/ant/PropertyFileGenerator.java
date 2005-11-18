/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler.ant;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;

import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 27, 2004
 */
public class PropertyFileGenerator extends Generator{
  private List<Pair<String, String>> myProperties = new ArrayList<Pair<String, String>>();

  public PropertyFileGenerator(Project project, GenerationOptions genOptions) {
    // path variables
    final PathMacrosImpl pathMacros = PathMacrosImpl.getInstanceEx();
    final Set<String> macroNamesSet = pathMacros.getUserMacroNames();
    if (macroNamesSet.size() > 0) {
      final String[] macroNames = macroNamesSet.toArray(new String[macroNamesSet.size()]);
      Arrays.sort(macroNames);
      for (int idx = 0; idx < macroNames.length; idx++) {
        final String macroName = macroNames[idx];
        addProperty(BuildProperties.getPathMacroProperty(macroName), pathMacros.getValue(macroName));
      }
    }
    // jdk homes
    if (genOptions.forceTargetJdk) {
      final ProjectJdk[] usedJdks = BuildProperties.getUsedJdks(project);
      for (int idx = 0; idx < usedJdks.length; idx++) {
        ProjectJdk jdk = usedJdks[idx];
        if (jdk.getHomeDirectory() == null) {
          continue;
        }
        final File home = VfsUtil.virtualToIoFile(jdk.getHomeDirectory());
        File homeDir;
        try {
          homeDir = home.getCanonicalFile();
        }
        catch (IOException e) {
          homeDir = home;
        }
        addProperty(BuildProperties.getJdkHomeProperty(jdk.getName()), homeDir.getPath().replace(File.separatorChar, '/'));
      }
    }
  }

  public void addProperty(String name, String value) {
    myProperties.add(new Pair<String, String>(name, value));
  }

  public void generate(DataOutput out) throws IOException {
    boolean isFirst = true;
    for (Iterator<Pair<String, String>> it = myProperties.iterator(); it.hasNext();) {
      final Pair<String, String> pair = (Pair<String, String>)it.next();
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
