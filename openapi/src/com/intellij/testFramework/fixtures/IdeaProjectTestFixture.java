/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

/**
 * This is to be provided by IDEA and not by plugin authors.
 */
public interface IdeaProjectTestFixture extends IdeaTestFixture {
  Project getProject();
  Module getModule();
}
