/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.impl;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomService;
import com.intellij.util.xml.ModelMerger;
import com.intellij.util.xml.ModelMergerImpl;
import com.intellij.util.xml.structure.DomStructureViewBuilder;

/**
 * @author Gregory.Shrago
 */
public class DomServiceImpl extends DomService {

  public ModelMerger createModelMerger() {
    return new ModelMergerImpl();
  }


  public StructureViewBuilder createSimpleStructureViewBuilder(final XmlFile file, final Function<DomElement, StructureViewMode> modeProvider) {
    return new DomStructureViewBuilder(file, modeProvider);
  }

}
