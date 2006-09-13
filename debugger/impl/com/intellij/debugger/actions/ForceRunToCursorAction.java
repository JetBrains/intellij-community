/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.actions;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 12, 2006
 */
public class ForceRunToCursorAction extends RunToCursorAction{
  public ForceRunToCursorAction() {
    super(true);
  }
}
