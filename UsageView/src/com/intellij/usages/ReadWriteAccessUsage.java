/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.usages;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 17, 2005
 */
public interface ReadWriteAccessUsage extends Usage{
  boolean isAccessedForWriting();
  boolean isAccessedForReading();
}
