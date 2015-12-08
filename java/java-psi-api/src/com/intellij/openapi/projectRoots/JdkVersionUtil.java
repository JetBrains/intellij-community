/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.projectRoots;

import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

public class JdkVersionUtil {
  private static final Map<JavaSdkVersion, String[]> VERSION_STRINGS = new EnumMap<JavaSdkVersion, String[]>(JavaSdkVersion.class);

  static {
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_0, new String[]{"1.0"});
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_1, new String[]{"1.1"});
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_2, new String[]{"1.2"});
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_3, new String[]{"1.3"});
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_4, new String[]{"1.4"});
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_5, new String[]{"1.5", "5.0"});
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_6, new String[]{"1.6", "6.0"});
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_7, new String[]{"1.7", "7.0"});
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_8, new String[]{"1.8", "8.0"});
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_9, new String[]{"1.9", "9.0", "9-ea"});
  }

  public static JavaSdkVersion getVersion(@NotNull String versionString) {
    for (Map.Entry<JavaSdkVersion, String[]> entry : VERSION_STRINGS.entrySet()) {
      for (String s : entry.getValue()) {
        if (versionString.contains(s)) {
          return entry.getKey();
        }
      }
    }
    return null;
  }
}
