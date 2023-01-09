// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java;

import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;

public class JpsJavaSdkType extends JpsSdkType<JpsDummyElement> implements JpsElementTypeWithDefaultProperties<JpsDummyElement> {
  public static final JpsJavaSdkType INSTANCE = new JpsJavaSdkType();

  @NotNull
  @Override
  public JpsDummyElement createDefaultProperties() {
    return JpsElementFactory.getInstance().createDummyElement();
  }

  @Override
  public String getPresentableName() {
    return "JDK";
  }

  public static @NotNull String getJavaExecutable(JpsSdk<?> sdk) {
    return sdk.getHomePath() + "/bin/java";
  }

  @Override
  public String toString() {
    return "java sdk type";
  }

  public static int getJavaVersion(@Nullable JpsSdk<?> sdk) {
    return parseVersion(sdk != null && sdk.getSdkType() instanceof JpsJavaSdkType ? sdk.getVersionString() : null);
  }

  public static int parseVersion(String javaVersionString) {
    JavaVersion version = JavaVersion.tryParse(javaVersionString);
    return version != null ? version.feature : 0;
  }

  /**
   * A value to pass with "-source", "-target" and "--release" compiler options.
   * Should work for Javac as well as ECJ-based compilers.
   */
  @NotNull
  public static String complianceOption(@NotNull JavaVersion version) {
    // for "-source" and "-target" options, a compiler accepts both "x" and "1.x" formats; for "--release" - only "x"
    return version.feature < 5 ? "1." + version.feature : String.valueOf(version.feature);
  }
}