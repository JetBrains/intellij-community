// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JdkUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p>Command line has length limit depending on used OS. In order to allow java command lines of any length for any OS,
 * a number of approaches are possible.</p>
 *
 * <p>Since 2017.3, it's possible to set up a command line shortening method per run configuration,
 * e.g. {@link com.intellij.execution.application.JvmMainMethodRunConfigurationOptions#getShortenClasspath()}.</p>
 */
public enum ShortenCommandLine {
  NONE("none", "java [options] classname [args]"),
  MANIFEST("JAR manifest", "java -cp classpath.jar classname [args]"),
  CLASSPATH_FILE("classpath file", "java WrapperClass classpathFile [args]"){
    @Override
    public boolean isApplicable(String jreRoot) {
      return jreRoot == null || !JdkUtil.isModularRuntime(jreRoot);
    }
  },
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

  public static @NotNull ShortenCommandLine getDefaultMethod(@Nullable Project project, String rootPath) {
    if (!JdkUtil.useDynamicClasspath(project)) return NONE;
    if (rootPath != null && JdkUtil.isModularRuntime(rootPath)) return ARGS_FILE;
    if (JdkUtil.useClasspathJar()) return MANIFEST;
    return CLASSPATH_FILE;
  }

  /** @deprecated do not use in a new code */
  @Deprecated
  public static ShortenCommandLine readShortenClasspathMethod(@NotNull Element element) {
    Element mode = element.getChild("shortenClasspath");
    return mode != null ? valueOf(mode.getAttributeValue("name")) : null;
  }

  /** @deprecated do not use in a new code */
  @Deprecated
  public static void writeShortenClasspathMethod(@NotNull Element element, ShortenCommandLine shortenCommandLine) {
    if (shortenCommandLine != null) {
      element.addContent(new Element("shortenClasspath").setAttribute("name", shortenCommandLine.name()));
    }
  }
}