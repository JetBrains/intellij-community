/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.cvsIntegration;


public interface DateOrRevision {
  public String getBranch();
  public boolean souldUseBranch();
  public String getDate();
  public boolean shouldUseDate();
}
