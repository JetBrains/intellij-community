/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.localVcs;



/**
 * author: lesya
 */

public interface LocalVcsPurgingProvider {

  void registerLocker(LocalVcsItemsLocker locker);
  void unregisterLocker(LocalVcsItemsLocker locker);

  boolean itemCanBePurged(LvcsRevision lvcsRevisionFor);
}
