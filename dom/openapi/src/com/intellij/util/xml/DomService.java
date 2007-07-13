/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;

/**
 * @author Gregory.Shrago
 */
public abstract class DomService {

  public static DomService getInstance() {
    return ServiceManager.getService(DomService.class);
  }

  public abstract ModelMerger createModelMerger();

  public enum StructureViewMode {
    SHOW, SHOW_CHILDREN, SKIP
  }
  public abstract StructureViewBuilder createSimpleStructureViewBuilder(final XmlFile file, final Function<DomElement, StructureViewMode> modeProvider);
}
