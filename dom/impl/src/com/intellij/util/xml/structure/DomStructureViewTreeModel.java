/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.structure;

import com.intellij.ide.structureView.impl.xml.XmlStructureViewTreeModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementNavigationProvider;
import com.intellij.util.xml.DomElementsNavigationManager;
import com.intellij.util.xml.DomService;
import com.intellij.util.Function;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Gregory.Shrago
*/
public class DomStructureViewTreeModel extends XmlStructureViewTreeModel implements Disposable {
  private DomElement myElement;
  private DomElementNavigationProvider myNavigationProvider;
  private final Function<DomElement, DomService.StructureViewMode> myDescriptor;

  public DomStructureViewTreeModel(@NotNull final DomElement mapping, Project project, final Function<DomElement, DomService.StructureViewMode> descriptor) {
    this(mapping, DomElementsNavigationManager.getManager(project).getDomElementsNavigateProvider(DomElementsNavigationManager.DEFAULT_PROVIDER_NAME), descriptor);
  }

  public DomStructureViewTreeModel(@NotNull final DomElement element, final DomElementNavigationProvider navigationProvider, final Function<DomElement, DomService.StructureViewMode> descriptor) {
    super(element.getRoot().getFile());

    myElement = element;
    myNavigationProvider = navigationProvider;
    myDescriptor = descriptor;
  }

  @NotNull
  public StructureViewTreeElement getRoot() {
    return new DomStructureTreeElement(myElement, myDescriptor, myNavigationProvider);
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
    return myElement.getRoot().getFile();
  }
}
