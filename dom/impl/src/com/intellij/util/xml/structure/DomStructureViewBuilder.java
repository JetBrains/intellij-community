/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.structure;

import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;

public class DomStructureViewBuilder extends TreeBasedStructureViewBuilder {
  private final DomElement myRoot;
  private final Function<DomElement, DomService.StructureViewMode> myDescriptor;

  public DomStructureViewBuilder(final DomElement root, final Function<DomElement,DomService.StructureViewMode> descriptor) {
    myRoot = root;
    myDescriptor = descriptor;
  }

  @NotNull
  public StructureViewModel createStructureViewModel() {
    return new DomStructureViewTreeModel(myRoot, myRoot.getManager().getProject(), myDescriptor);
  }

  public boolean isRootNodeShown() {
    return true;
  }

  @NotNull
  public StructureView createStructureView(final FileEditor fileEditor, final Project project) {
    return new StructureViewComponent(fileEditor, createStructureViewModel(), project) {
      public DefaultMutableTreeNode expandPathToElement(final Object element) {
        if (element instanceof XmlElement) {
          final XmlElement xmlElement = (XmlElement)element;
          XmlTag tag = PsiTreeUtil.getParentOfType(xmlElement, XmlTag.class, false);
          while (tag != null) {
            final DomElement domElement = DomManager.getDomManager(xmlElement.getProject()).getDomElement(tag);
            if (domElement != null) {
              for (DomElement curElement = domElement; curElement != null; curElement = curElement.getParent()) {
                if (myDescriptor.fun(curElement) == DomService.StructureViewMode.SHOW) {
                  return super.expandPathToElement(curElement.getXmlElement());
                }
              }
            }
            tag = PsiTreeUtil.getParentOfType(tag, XmlTag.class, true);
          }

        }
        return super.expandPathToElement(element);
      }
    };
  }
}