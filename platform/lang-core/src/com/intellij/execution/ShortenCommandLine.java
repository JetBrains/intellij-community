// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.lang.LangCoreBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

/**
 * <p>Command line has length limit depending on used OS. In order to allow java command lines of any length for any OS,
 * a number of approaches are possible.</p>
 *
 * <p>Since 2017.3, it's possible to set up a command line shortening method per run configuration,
 * e.g. {@link com.intellij.execution.application.JvmMainMethodRunConfigurationOptions#getShortenClasspath()}.</p>
 */
public enum ShortenCommandLine {
  NONE("shorten.command.line.method.none", "java [options] className [args]"),
  MANIFEST("shorten.command.line.method.jar.manifest", "java -cp classpath.jar className [args]"),
  CLASSPATH_FILE("shorten.command.line.method.classpath.file", "java WrapperClass classpathFile className [args]") {
    @Override
    public boolean isApplicable(String jreRoot) {
      return jreRoot == null || !JdkUtil.isModularRuntime(jreRoot);
    }
  },
  ARGS_FILE("shorten.command.line.method.argfile", "java @argfile className [args]") {
    @Override
    public boolean isApplicable(String jreRoot) {
      return jreRoot == null || JdkUtil.isModularRuntime(jreRoot);
    }
  };

  private final @PropertyKey(resourceBundle = LangCoreBundle.BUNDLE) String myNameKey;
  private final @NlsSafe String myDescription;

  ShortenCommandLine(@PropertyKey(resourceBundle = LangCoreBundle.BUNDLE) String nameKey, @NlsSafe String description) {
    myNameKey = nameKey;
    myDescription = description;
  }

  public boolean isApplicable(String jreRoot) {
    return true;
  }

  public @NlsContexts.Label String getDescription() {
    return myDescription;
  }

  public @NlsContexts.Label String getPresentableName() {
    return LangCoreBundle.message(myNameKey);
  }

  public static @NotNull ShortenCommandLine getDefaultMethod(@Nullable Project project, String rootPath) {
    if (!JdkUtil.useDynamicClasspath(project)) return NONE;
    return getDefaultMethodForJdkLevel(rootPath);
  }

  public static @NotNull ShortenCommandLine getDefaultMethodForJdkLevel(@Nullable String rootPath) {
    if (rootPath != null && JdkUtil.isModularRuntime(rootPath)) return ARGS_FILE;
    if (JdkUtil.useClasspathJar()) return MANIFEST;
    return CLASSPATH_FILE;
  }

  /** @deprecated do not use in a new code */
  @Deprecated(forRemoval = true)
  public static ShortenCommandLine readShortenClasspathMethod(@NotNull Element element) {
    Element mode = element.getChild("shortenClasspath");
    return mode != null ? valueOf(mode.getAttributeValue("name")) : null;
  }

  /** @deprecated do not use in a new code */
  @Deprecated(forRemoval = true)
  public static void writeShortenClasspathMethod(@NotNull Element element, ShortenCommandLine shortenCommandLine) {
    if (shortenCommandLine != null) {
      element.addContent(new Element("shortenClasspath").setAttribute("name", shortenCommandLine.name()));
    }
  }
}
