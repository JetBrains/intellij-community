/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;

/**
 * @author nik
 */
public class JpsJavaSdkType extends JpsSdkType<JpsDummyElement> implements JpsElementTypeWithDefaultProperties<JpsDummyElement> {
  public static final JpsJavaSdkType INSTANCE = new JpsJavaSdkType();

  @NotNull
  @Override
  public JpsDummyElement createDefaultProperties() {
    return JpsElementFactory.getInstance().createDummyElement();
  }

  public static String getJavaExecutable(JpsSdk<?> sdk) {
    return sdk.getHomePath() + "/bin/java";
  }

  @Override
  public String toString() {
    return "java sdk type";
  }

  public static int getJavaVersion(@Nullable JpsSdk<?> sdk) {
    return parseVersion(sdk != null && sdk.getSdkType() instanceof JpsJavaSdkType? sdk.getVersionString() : null);
  }

  public static int parseVersion(String javaVersionString) {
    if (javaVersionString == null) {
      return 0;
    }
    final int quoteBegin = javaVersionString.indexOf('\"');
    if (quoteBegin >= 0) {
      final int quoteEnd = javaVersionString.indexOf('\"', quoteBegin + 1);
      if (quoteEnd > quoteBegin) {
        javaVersionString = javaVersionString.substring(quoteBegin + 1, quoteEnd);
      }
    }
    if (javaVersionString.isEmpty()) {
      return 0;
    }

    final String prefix = "1.";
    final int parseBegin = javaVersionString.startsWith(prefix) ? prefix.length() : 0;

    int parseEnd = parseBegin;
    while (parseEnd < javaVersionString.length()) {
      if (!Character.isDigit(javaVersionString.charAt(parseEnd))) {
        break;
      }
      parseEnd++;
    }
    try {
      return Integer.parseInt(javaVersionString.substring(parseBegin, parseEnd));
    }
    catch (NumberFormatException ignored) {
    }
    return 0;
  }

}
