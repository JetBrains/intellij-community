/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml;

import com.intellij.openapi.components.ServiceManager;

/**
 * @author Gregory.Shrago
 */
public abstract class DomService {

  public static DomService getInstance() {
    return ServiceManager.getService(DomService.class);
  }

  public abstract ModelMerger createModelMerger();
}
