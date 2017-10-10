// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.execution;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JdkUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * Command line has length limit depending on used OS. In order to allow java command lines of any length for any OS, a number of approaches are possible.
 *
 * Since 2017.3, it's possible to setup shortening command line method per run configuration, e.g. {@link com.intellij.execution.CommonJavaRunConfigurationParameters#getShortenClasspath}
 */
public enum ShortenCommandLine {
  NONE("none", "java [options] classname [args]"),
  MANIFEST("JAR manifest", "java -cp classpath.jar classname [args]"),
  CLASSPATH_FILE("classpath file", "java WrapperClass classpathFile [args]"),
  ARGS_FILE("@argFiles (java 9+)", "java @argFile [args]") {
    @Override
    public boolean isApplicable(String jreRoot) {
      return jreRoot != null && JdkUtil.isModularRuntime(jreRoot);
    }
  };

  private final String myPresentableName;
  private final String myDescription;

  ShortenCommandLine(String presentableName, String description) {
    myPresentableName = presentableName;
    myDescription = description;
  }

  public boolean isApplicable(String jreRoot) {
    return true;
  }

  public String getDescription() {
    return myDescription;
  }

  public String getPresentableName() {
    return myPresentableName;
  }

  public static ShortenCommandLine getDefaultMethod(Project project, String rootPath) {
    if (!JdkUtil.useDynamicClasspath(project)) return NONE;
    if (rootPath != null && JdkUtil.isModularRuntime(rootPath)) return ARGS_FILE;
    if (JdkUtil.useClasspathJar()) return MANIFEST;
    return CLASSPATH_FILE;
  }

  public static ShortenCommandLine readShortenClasspathMethod(@NotNull Element element) {
    Element mode = element.getChild("shortenClasspath");
    if (mode != null) {
      return valueOf(mode.getAttributeValue("name"));
    }
    return null;
  }

  public static void writeShortenClasspathMethod(@NotNull Element element, ShortenCommandLine shortenCommandLine) {
    if (shortenCommandLine != null) {
      element.addContent(new Element("shortenClasspath").setAttribute("name", shortenCommandLine.name()));
    }
  }
}
