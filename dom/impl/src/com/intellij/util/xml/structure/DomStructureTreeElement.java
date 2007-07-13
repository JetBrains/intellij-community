/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.structure;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.util.Function;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;

/**
 * @author Gregory.Shrago
 */
public class DomStructureTreeElement implements StructureViewTreeElement, ItemPresentation {
  private final DomElement myElement;
  private final Function<DomElement, DomService.StructureViewMode> myDescriptor;
  private final DomElementNavigationProvider myNavigationProvider;

  public DomStructureTreeElement(final DomElement element, final Function<DomElement,DomService.StructureViewMode> descriptor, final DomElementNavigationProvider navigationProvider) {
    myElement = element;
    myDescriptor = descriptor;
    myNavigationProvider = navigationProvider;
  }

  public DomElement getElement() {
    return myElement;
  }

  public DomElementNavigationProvider getNavigationProvider() {
    return myNavigationProvider;
  }

  public Object getValue() {
    return !myElement.isValid() ? null : myElement.getXmlElement();
  }

  public ItemPresentation getPresentation() {
    return this;
  }

  public TreeElement[] getChildren() {
    if (!myElement.isValid()) return EMPTY_ARRAY;
    final ArrayList<TreeElement> result = new ArrayList<TreeElement>();
    final DomElementVisitor elementVisitor = new DomElementVisitor() {
      public void visitDomElement(final DomElement element) {
        final DomService.StructureViewMode viewMode = myDescriptor.fun(element);
        switch (viewMode) {
          case SHOW:
            result.add(new DomStructureTreeElement(element, myDescriptor, myNavigationProvider));
            break;
          case SHOW_CHILDREN:
            DomUtil.acceptAvailableChildren(element, this);
            break;
          case SKIP:
            break;
        }
      }
    };
    DomUtil.acceptAvailableChildren(myElement, elementVisitor);
    return result.toArray(new TreeElement[result.size()]);
  }

  public void navigate(boolean requestFocus) {
    if (myNavigationProvider != null) myNavigationProvider.navigate(myElement, true);
  }

  public boolean canNavigate() {
    return myNavigationProvider != null && myNavigationProvider.canNavigate(myElement);
  }

  public boolean canNavigateToSource() {
    return myNavigationProvider != null && myNavigationProvider.canNavigate(myElement);
  }

  public String getPresentableText() {
    if (!myElement.isValid()) return "<unknown>";
    final ElementPresentation presentation = myElement.getPresentation();
    final String name = presentation.getElementName();
    return name != null? name : presentation.getTypeName();
  }

  @Nullable
  public String getLocationString() {
    return null;
  }

  @Nullable
  public Icon getIcon(boolean open) {
    if (!myElement.isValid()) return null;
    return myElement.getPresentation().getIcon();
  }

  @Nullable
  public TextAttributesKey getTextAttributesKey() {
    return null;
  }
}
