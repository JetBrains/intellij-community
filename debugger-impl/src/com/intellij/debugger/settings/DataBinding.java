/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.settings;

/**
 * @author Eugene Zhuravlev
 * Date: Apr 12, 2005
 */
public interface DataBinding {
  void loadData(Object from);
  void saveData(Object to);
  boolean isModified(Object obj);
}
