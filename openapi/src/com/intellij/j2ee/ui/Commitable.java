/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.j2ee.ui;

import com.intellij.j2ee.j2eeDom.xmlData.ReadOnlyDeploymentDescriptorModificationException;

import java.util.List;

/**
 * author: lesya
 */
public interface Commitable {
  void commit() throws ReadOnlyDeploymentDescriptorModificationException;

  void reset();

  void dispose();

  List<Warning> getWarnings();
}
