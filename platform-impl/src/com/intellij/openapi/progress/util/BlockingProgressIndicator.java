/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.progress.util;

public abstract class BlockingProgressIndicator extends ProgressIndicatorBase {
  public abstract void startBlocking();
}