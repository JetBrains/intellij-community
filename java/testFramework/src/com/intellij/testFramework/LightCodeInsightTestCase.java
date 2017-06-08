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
package com.intellij.testFramework;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A TestCase for single PsiFile being opened in Editor conversion. See configureXXX and checkResultXXX method docs.
 */
public abstract class LightCodeInsightTestCase extends LightPlatformCodeInsightTestCase {
  private static final Pattern JDK_SELECT_PATTERN = Pattern.compile("Java([\\d.]+)(\\.java)?$");

  public static JavaPsiFacadeEx getJavaFacade() {
    return JavaPsiFacadeEx.getInstanceEx(ourProject);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setLanguageLevel(getLanguageLevel());
  }

  /**
   * Returns a language level for test. Could be overridden for specific behavior.
   * <p>
   * This implementation checks the test name. If it ends with JavaXYZ.java and
   * XYZ is known Java version (e.g. Java1.4.java or Java9.java), then version XYZ is returned.
   * Otherwise {@link #getDefaultLanguageLevel() default version} is returned.
   *
   * @return a project language level for test.
   */
  protected LanguageLevel getLanguageLevel() {
    Matcher matcher = JDK_SELECT_PATTERN.matcher(getTestName(false));
    if(matcher.find()) {
      LanguageLevel level = LanguageLevel.parse(matcher.group(1));
      if (level != null) {
        return level;
      }
    }

    return getDefaultLanguageLevel();
  }

  /**
   * @return default language level if it's not forced by test name
   */
  protected LanguageLevel getDefaultLanguageLevel() {
    return LanguageLevel.HIGHEST;
  }

  protected void setLanguageLevel(LanguageLevel level) {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(level);
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk18();
  }

  @NotNull
  @Override
  protected ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }
}