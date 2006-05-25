/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures;

/**
 * This is to be provided by IDEA and not by plugin authors.
 */
public interface IdeaTestFixture {
  void setUp() throws Exception;
  void tearDown() throws Exception;
}
