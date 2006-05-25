/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures;

import com.intellij.openapi.module.ModuleType;
import com.intellij.pom.java.LanguageLevel;

/**
 * @author mike
 */
public interface TestFixtureBuilder<T extends IdeaTestFixture> {
  T setModuleType(ModuleType moduleType);
  T setLanguageLevel(LanguageLevel languageLevel);

  T getFixture();
}
