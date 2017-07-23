/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.openapi.projectRoots;

import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JavaSdkVersionTest {
  @Test
  public void sdkVersionFromLanguageLevel() {
    assertEquals(JavaSdkVersion.JDK_1_3, JavaSdkVersion.fromLanguageLevel(LanguageLevel.JDK_1_3));
    assertEquals(JavaSdkVersion.JDK_1_6, JavaSdkVersion.fromLanguageLevel(LanguageLevel.JDK_1_6));
    assertEquals(JavaSdkVersion.JDK_1_8, JavaSdkVersion.fromLanguageLevel(LanguageLevel.JDK_1_8));
  }

  @Test
  public void sdkVersionFromVersionString() {
    assertEquals(JavaSdkVersion.JDK_1_8, JavaSdkVersion.fromVersionString("1.8.0_131"));
    assertEquals(JavaSdkVersion.JDK_1_9, JavaSdkVersion.fromVersionString("9"));
    assertEquals(JavaSdkVersion.JDK_1_9, JavaSdkVersion.fromVersionString("9-ea"));
    assertEquals(JavaSdkVersion.JDK_1_9, JavaSdkVersion.fromVersionString("9.1.2"));

    assertEquals(JavaSdkVersion.JDK_1_8, JavaSdkVersion.fromVersionString("java version \"1.8.0_131\""));
    assertEquals(JavaSdkVersion.JDK_1_9, JavaSdkVersion.fromVersionString("java version \"9\""));
    assertEquals(JavaSdkVersion.JDK_1_9, JavaSdkVersion.fromVersionString("java version \"9-ea\""));
    assertEquals(JavaSdkVersion.JDK_1_9, JavaSdkVersion.fromVersionString("java version \"9.1.2\""));
  }
}