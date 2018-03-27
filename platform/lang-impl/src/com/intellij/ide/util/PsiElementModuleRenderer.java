// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class PsiElementModuleRenderer extends DefaultListCellRenderer{
  private String myText;

  @Override
  public Component getListCellRendererComponent(
    JList list,
    Object value,
    int index,
    boolean isSelected,
    boolean cellHasFocus) {
    final Component listCellRendererComponent = super.getListCellRendererComponent(list, null, index, isSelected, cellHasFocus);
    customizeCellRenderer(value, index, isSelected, cellHasFocus);
    return listCellRendererComponent;
  }

  @Override
  public String getText() {
    return myText;
  }

  protected void customizeCellRenderer(
    Object value,
    int index,
    boolean selected,
    boolean hasFocus
  ) {
    myText = "";
    if (value instanceof PsiElement) {
      PsiElement element = (PsiElement)value;
      if (element.isValid()) {
        PsiFile psiFile = element.getContainingFile();
        Module module = ModuleUtil.findModuleForPsiElement(element);
        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
        boolean isInLibraries = false;
        if (psiFile != null) {
          VirtualFile vFile = psiFile.getVirtualFile();
          if (vFile != null) {
            isInLibraries = fileIndex.isInLibrarySource(vFile) || fileIndex.isInLibraryClasses(vFile);
            if (isInLibraries){
              showLibraryLocation(fileIndex, vFile);
            }
          }
        }
        if (module != null && !isInLibraries) {
          showProjectLocation(psiFile, module, fileIndex);
        }
      }
    }

    setText(myText);
    setBorder(BorderFactory.createEmptyBorder(0, 0, 0, UIUtil.getListCellHPadding()));
    setHorizontalTextPosition(SwingConstants.LEFT);
    setHorizontalAlignment(SwingConstants.RIGHT); // align icon to the right
    setBackground(selected ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground());
    setForeground(selected ? UIUtil.getListSelectionForeground() : UIUtil.getInactiveTextColor());
  }

  private void showProjectLocation(PsiFile psiFile, Module module, ProjectFileIndex fileIndex) {
    boolean inTestSource = false;
    if (psiFile != null) {
      VirtualFile vFile = psiFile.getVirtualFile();
      if (vFile != null) {
        inTestSource = fileIndex.isInTestSourceContent(vFile);
      }
    }
    myText = module.getName();
    if (inTestSource) {
      setIcon(AllIcons.Modules.TestSourceFolder);
    }
    else {
      setIcon(ModuleType.get(module).getIcon());
    }
  }

  private void showLibraryLocation(ProjectFileIndex fileIndex, VirtualFile vFile) {
    setIcon(AllIcons.Nodes.PpLibFolder);
    for (OrderEntry order : fileIndex.getOrderEntriesForFile(vFile)) {
      if (order instanceof LibraryOrderEntry || order instanceof JdkOrderEntry) {
        myText = getPresentableName(order, vFile);
        break;
      }
    }

    myText = myText.substring(myText.lastIndexOf(File.separatorChar) + 1);
    VirtualFile jar = JarFileSystem.getInstance().getVirtualFileForJar(vFile);
    if (jar != null && !myText.equals(jar.getName())) {
      myText += " (" + jar.getName() + ")";
    }
  }

  protected String getPresentableName(final OrderEntry order, final VirtualFile vFile) {
    return order.getPresentableName();
  }
}
