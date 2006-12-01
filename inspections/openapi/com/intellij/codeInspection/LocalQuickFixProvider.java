/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 30-Nov-2006
 */
package com.intellij.codeInspection;

public interface LocalQuickFixProvider {
  LocalQuickFix[] getQuickFixes();
}