/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.navigationToolbar;

import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
* @author Konstantin Bulenkov
*/
public class NavBarListCellRenderer extends ColoredListCellRenderer {
  private final Project myProject;
  private final NavBarPanel myPanel;

  NavBarListCellRenderer(Project project, NavBarPanel panel) {
    myProject = project;
    myPanel = panel;
  }

  @Override
  protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
    setFocusBorderAroundIcon(false);
    final String name = myPanel.getPresentation().getPresentableText(value);

    Color color = list.getForeground();
    boolean isProblemFile = false;
    if (value instanceof PsiElement) {
      final PsiElement psiElement = (PsiElement)value;
      PsiFile psiFile = psiElement.getContainingFile();
      if (psiFile != null) {
        VirtualFile vFile = psiFile.getVirtualFile();
        if (vFile != null) {
          if (WolfTheProblemSolver.getInstance(myProject).isProblemFile(vFile)) {
            isProblemFile = true;
          }
          FileStatus status = FileStatusManager.getInstance(myProject).getStatus(vFile);
          color = status.getColor();
        }
      }
      else {
        isProblemFile = NavBarPresentation.wolfHasProblemFilesBeneath(psiElement);
      }
    }
    else if (value instanceof Module) {
      final Module module = (Module)value;
      isProblemFile = WolfTheProblemSolver.getInstance(myProject).hasProblemFilesBeneath(module);
    }
    else if (value instanceof Project) {
      final Module[] modules = ModuleManager.getInstance((Project)value).getModules();
      for (Module module : modules) {
        if (WolfTheProblemSolver.getInstance(myProject).hasProblemFilesBeneath(module)) {
          isProblemFile = true;
          break;
        }
      }
    }
    final SimpleTextAttributes nameAttributes;
    if (isProblemFile) {
      TextAttributes attributes = new TextAttributes(color, null, JBColor.RED, EffectType.WAVE_UNDERSCORE, Font.PLAIN);
      nameAttributes = SimpleTextAttributes.fromTextAttributes(attributes);
    }
    else {
      nameAttributes = new SimpleTextAttributes(Font.PLAIN, color);
    }
    append(name, nameAttributes);
    // manually set icon opaque to prevent background artifacts
    setIconOpaque(false);
    setIcon(myPanel.getPresentation().getIcon(value));
    setPaintFocusBorder(false);
    setBackground(selected ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground());
  }
}
