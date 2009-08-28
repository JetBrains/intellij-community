/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.pom;

/**
 * @author peter
 */
public interface PomNamedTarget extends PomTarget {
  PomNamedTarget[] EMPTY_ARRAY = new PomNamedTarget[0];

  String getName();

}
