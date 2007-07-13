/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.structure;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.xml.XmlStructureViewTreeModel;
import com.intellij.ide.structureView.impl.xml.XmlFileTreeElement;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.openapi.Disposable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Gregory.Shrago
*/
public class DomStructureViewTreeModel extends XmlStructureViewTreeModel implements Disposable {
  private final XmlFile myFile;
  private final DomElementNavigationProvider myNavigationProvider;
  private final Function<DomElement, DomService.StructureViewMode> myDescriptor;

  public DomStructureViewTreeModel(final XmlFile file, final Function<DomElement, DomService.StructureViewMode> descriptor) {
    this(file, DomElementsNavigationManager.getManager(file.getProject()).getDomElementsNavigateProvider(DomElementsNavigationManager.DEFAULT_PROVIDER_NAME), descriptor);
  }

  public DomStructureViewTreeModel(final XmlFile file, final DomElementNavigationProvider navigationProvider, final Function<DomElement, DomService.StructureViewMode> descriptor) {
    super(file);
    myFile = file;
    myNavigationProvider = navigationProvider;
    myDescriptor = descriptor;
  }

  @NotNull
  public StructureViewTreeElement getRoot() {
    final DomFileElement<DomElement> fileElement = DomManager.getDomManager(myFile.getProject()).getFileElement(myFile, DomElement.class);
    return fileElement == null?
           new XmlFileTreeElement(myFile) :
           new DomStructureTreeElement(fileElement.getRootElement().createStableCopy(), myDescriptor, myNavigationProvider);
  }

  @NotNull
  public Grouper[] getGroupers() {
    return Grouper.EMPTY_ARRAY;
  }

  @NotNull
  public Sorter[] getSorters() {
    return new Sorter[]{Sorter.ALPHA_SORTER};
  }

  @NotNull
  public Filter[] getFilters() {
    return Filter.EMPTY_ARRAY;
  }

  protected PsiFile getPsiFile() {
    return myFile;
  }
}
