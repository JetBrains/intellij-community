/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ide.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public class PsiElementModuleRenderer extends DefaultListCellRenderer{
  private static final Icon TEST_ICON = IconLoader.getIcon("/nodes/testSourceFolder.png");
  private static final Icon LIB_ICON = IconLoader.getIcon("/nodes/ppLibClosed.png");

  private String myText;

  public Component getListCellRendererComponent(
    JList list,
    Object value,
    int index,
    boolean isSelected,
    boolean cellHasFocus) {
    final Component listCellRendererComponent = super.getListCellRendererComponent(list, value, index, isSelected,
                                                                                   cellHasFocus);
    customizeCellRenderer(value, index, isSelected, cellHasFocus);
    return listCellRendererComponent;
  }

  public String getText() {
    return myText;
  }

  protected void customizeCellRenderer(
    Object value,
    int index,
    boolean selected,
    boolean hasFocus
  ) {
    if (value instanceof PsiElement) {
      PsiElement element = (PsiElement)value;
      if (element.isValid()) {
        PsiFile psiFile = element.getContainingFile();
        Module module = ModuleUtil.findModuleForPsiElement(element);
        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
        if (module != null) {
          boolean inTestSource = false;
          if (psiFile != null) {
            VirtualFile vFile = psiFile.getVirtualFile();
            if (vFile != null) {
              inTestSource = fileIndex.isInTestSourceContent(vFile);
            }
          }
          myText = module.getName();
          if (inTestSource) {
            setIcon(TEST_ICON);
          }
          else {
            setIcon(module.getModuleType().getNodeIcon(false));
          }
        } else {
          if (psiFile != null) {
            VirtualFile vFile = psiFile.getVirtualFile();
            if (vFile != null) {
              final boolean isInLibraries = fileIndex.isInLibrarySource(vFile) || fileIndex.isInLibraryClasses(vFile);
              if (isInLibraries){
                setIcon(LIB_ICON);
                for (OrderEntry order : fileIndex.getOrderEntriesForFile(vFile)) {
                  if (order instanceof LibraryOrderEntry || order instanceof JdkOrderEntry) {
                    myText = order.getPresentableName();
                    break;
                  }
                }
                VirtualFile jar = JarFileSystem.getInstance().getVirtualFileForJar(vFile);
                if (jar != null) {
                  myText += " (" + jar.getName() + ")";
                }
              } /*else {
              setIcon(IconUtilEx.getEmptyIcon(false));
              setText(value.toString());
            }*/
            }
          }
        }
      }
      else {
        myText = "";
      }
    }
    /*else {
      setIcon(IconUtilEx.getEmptyIcon(false));
      setText(value == null ? "" : value.toString());
    }*/
    setText(myText);
    setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));
    setHorizontalTextPosition(SwingConstants.LEFT);
    setBackground(selected ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground());
    setForeground(selected ? UIUtil.getListSelectionForeground() : UIUtil.getInactiveTextColor());

    if (UIUtil.isUnderNimbusLookAndFeel()) {
      setOpaque(false);
    }

  }
}
