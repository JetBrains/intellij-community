/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

/**
 * @author mike
 */
public interface IdeaTestFixture {
  void setUp() throws Exception;
  void tearDown() throws Exception;

  Project getProject();
  Module getModule();
}
