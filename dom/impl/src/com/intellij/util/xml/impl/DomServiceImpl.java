/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.impl;

import com.intellij.util.xml.DomService;
import com.intellij.util.xml.ModelMerger;
import com.intellij.util.xml.ModelMergerImpl;

/**
 * @author Gregory.Shrago
 */
public class DomServiceImpl extends DomService {

  public ModelMerger createModelMerger() {
    return new ModelMergerImpl();
  }

}
