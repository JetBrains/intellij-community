/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.localVcs;

public interface LvcsLabelListener {
  public void labelAdded(LvcsLabel label);
  public void labelDeleted(LvcsLabel label);
}
