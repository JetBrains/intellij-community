/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.codeStyle;

/**
 * @author MYakovlev
 * Date: Jul 19, 2002
 */
public interface CodeStyleScheme {
  CodeStyleScheme getParentScheme();
  String getName();
  boolean isDefault();
  CodeStyleSettings getCodeStyleSettings();
}
