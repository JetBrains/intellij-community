/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import java.util.List;

/**
 * @author peter
*/
public interface MergedObject<V> {
  List<V> getImplementations();
}
