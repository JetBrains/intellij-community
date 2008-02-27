/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.impl;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.xml.*;
import com.intellij.util.xml.structure.DomStructureViewBuilder;

import java.util.Collection;

/**
 * @author Gregory.Shrago
 */
public class DomServiceImpl extends DomService {

  public ModelMerger createModelMerger() {
    return new ModelMergerImpl();
  }

  public Collection<VirtualFile> getAllFiles(Class<? extends DomFileDescription> description, Project project) {
    return FileBasedIndex.getInstance().getContainingFiles(DomFileIndex.NAME, description.getName(), project);
  }


  public StructureViewBuilder createSimpleStructureViewBuilder(final XmlFile file, final Function<DomElement, StructureViewMode> modeProvider) {
    return new DomStructureViewBuilder(file, modeProvider);
  }

}
