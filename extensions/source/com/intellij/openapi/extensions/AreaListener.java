/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions;

/**
 * @author akireyev
 */
public interface AreaListener {
  void areaCreated(String areaClass, AreaInstance areaInstance);
  void areaDisposing(String areaClass, AreaInstance areaInstance);
}
