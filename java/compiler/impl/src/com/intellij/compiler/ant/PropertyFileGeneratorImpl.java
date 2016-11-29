/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.compiler.ant;

import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.ArrayUtil;

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
public class PropertyFileGeneratorImpl extends PropertyFileGenerator {
  /**
   * List of the properties
   */
  private final List<Couple<String>> myProperties = new ArrayList<>();

  /**
   * A constctor that extracts all neeed properties for ant build from the project.
   *
   * @param project    a project to examine
   * @param genOptions generation options
   */
  public PropertyFileGeneratorImpl(Project project, GenerationOptions genOptions) {
    // path variables
    final PathMacros pathMacros = PathMacros.getInstance();
    final Set<String> macroNamesSet = pathMacros.getUserMacroNames();
    if (macroNamesSet.size() > 0) {
      final String[] macroNames = ArrayUtil.toStringArray(macroNamesSet);
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
    // generate idea.home property
    if (genOptions.isIdeaHomeGenerated()) {
      addProperty(BuildProperties.PROPERTY_IDEA_HOME, PathManager.getHomePath());
    }

    if (genOptions.enableFormCompiler) {
      addProperty(BuildProperties.PROPERTY_INCLUDE_JAVA_RUNTIME_FOR_INSTRUMENTATION, genOptions.forceTargetJdk? "false" : "true");
    }

    ChunkBuildExtension.generateAllProperties(this, project, genOptions);
  }

  public void addProperty(String name, String value) {
    myProperties.add(Couple.of(name, value));
  }

  @Override
  public void generate(PrintWriter out) throws IOException {
    boolean isFirst = true;
    for (final Couple<String> pair : myProperties) {
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
