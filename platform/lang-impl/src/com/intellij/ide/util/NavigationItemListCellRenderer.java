/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.navigation.NavigationItemFileStatus;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.text.Matcher;
import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class NavigationItemListCellRenderer extends OpaquePanel implements ListCellRenderer, MatcherHolder {

  private Matcher myMatcher;

  public NavigationItemListCellRenderer() {
    super(new BorderLayout());
  }

  @Override
  public Component getListCellRendererComponent(
    JList list,
    Object value,
    int index,
    boolean isSelected,
    boolean cellHasFocus) {
    removeAll();

    final boolean hasRightRenderer = UISettings.getInstance().SHOW_ICONS_IN_QUICK_NAVIGATION;
    final ModuleRendererFactory factory = ModuleRendererFactory.findInstance(value);

    final LeftRenderer left = new LeftRenderer(!hasRightRenderer || !factory.rendersLocationString(), myMatcher);
    final Component leftCellRendererComponent = left.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    final Color listBg = leftCellRendererComponent.getBackground();
    add(leftCellRendererComponent, BorderLayout.WEST);

    setBackground(isSelected ? UIUtil.getListSelectionBackground() : listBg);

    if  (hasRightRenderer){
      final DefaultListCellRenderer moduleRenderer = factory.getModuleRenderer();

      final Component rightCellRendererComponent =
        moduleRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      ((JComponent)rightCellRendererComponent).setOpaque(false);
      rightCellRendererComponent.setBackground(listBg);
      add(rightCellRendererComponent, BorderLayout.EAST);
      final JPanel spacer = new NonOpaquePanel();

      final Dimension size = rightCellRendererComponent.getSize();
      spacer.setSize(new Dimension((int)(size.width * 0.015 + leftCellRendererComponent.getSize().width * 0.015), size.height));
      spacer.setBackground(isSelected ? UIUtil.getListSelectionBackground() : listBg);
      add(spacer, BorderLayout.CENTER);
    }
    return this;
  }

  @Override
  public void setPatternMatcher(final Matcher matcher) {
    myMatcher = matcher;
  }

  protected static Color getBackgroundColor(@Nullable Object value) {
    if (value instanceof PsiElement || value instanceof DataProvider) {
      final PsiElement psiElement = value instanceof PsiElement
                                    ? (PsiElement)value
                                    : CommonDataKeys.PSI_ELEMENT.getData((DataProvider) value);
      if (psiElement != null && psiElement.isValid()) {
        final FileColorManager fileColorManager = FileColorManager.getInstance(psiElement.getProject());
        final Color fileColor = fileColorManager.getRendererBackground(psiElement.getContainingFile());
        if (fileColor != null) {
          return fileColor;
        }
      }
    }

    return UIUtil.getListBackground();
  }

  private static class LeftRenderer extends ColoredListCellRenderer {
    public final boolean myRenderLocation;
    private final Matcher myMatcher;

    public LeftRenderer(boolean renderLocation, Matcher matcher) {
      myRenderLocation = renderLocation;
      myMatcher = matcher;
    }

    @Override
    protected void customizeCellRenderer(
      JList list,
      Object value,
      int index,
      boolean selected,
      boolean hasFocus
      ) {
      Color bgColor = UIUtil.getListBackground();

      if (value instanceof PsiElement && !((PsiElement)value).isValid()) {
        setIcon(IconUtil.getEmptyIcon(false));
        append("Invalid", SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
      else if (value instanceof NavigationItem) {
        NavigationItem element = (NavigationItem)value;
        ItemPresentation presentation = element.getPresentation();
        assert presentation != null: "PSI elements displayed in choose by name lists must return a non-null value from getPresentation(): element " +
          element.toString() + ", class " + element.getClass().getName();
        String name = presentation.getPresentableText();
        assert name != null: "PSI elements displayed in choose by name lists must return a non-null value from getPresentation().getPresentableName: element " +
                                     element.toString() + ", class " + element.getClass().getName();
        Color color = list.getForeground();
        boolean isProblemFile = element instanceof PsiElement
                                && WolfTheProblemSolver.getInstance(((PsiElement)element).getProject())
                                   .isProblemFile(PsiUtilCore.getVirtualFile((PsiElement)element));

        if (element instanceof PsiElement || element instanceof DataProvider) {
          final PsiElement psiElement = element instanceof PsiElement
                                        ? (PsiElement)element
                                        : CommonDataKeys.PSI_ELEMENT.getData((DataProvider) element);
          if (psiElement != null) {
            final Project project = psiElement.getProject();

            final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(psiElement);
            isProblemFile = WolfTheProblemSolver.getInstance(project).isProblemFile(virtualFile);

            final FileColorManager fileColorManager = FileColorManager.getInstance(project);
            final Color fileColor = fileColorManager.getRendererBackground(psiElement.getContainingFile());
            if (fileColor != null) {
              bgColor = fileColor;
            }
          }
        }

        FileStatus status = NavigationItemFileStatus.get(element);
        if (status != FileStatus.NOT_CHANGED) {
          color = status.getColor();
        }

        final TextAttributes textAttributes = NodeRenderer.getSimpleTextAttributes(presentation).toTextAttributes();
        if (isProblemFile) {
          textAttributes.setEffectType(EffectType.WAVE_UNDERSCORE);
          textAttributes.setEffectColor(JBColor.red);
        }
        textAttributes.setForegroundColor(color);
        SimpleTextAttributes nameAttributes = SimpleTextAttributes.fromTextAttributes(textAttributes);
        SpeedSearchUtil.appendColoredFragmentForMatcher(name,  this, nameAttributes, myMatcher, bgColor, selected);
        setIcon(presentation.getIcon(false));

        if (myRenderLocation) {
          String containerText = presentation.getLocationString();

          if (containerText != null && containerText.length() > 0) {
            append(" " + containerText, new SimpleTextAttributes(Font.PLAIN, JBColor.GRAY));
          }
        }
      }
      else {
        setIcon(IconUtil.getEmptyIcon(false));
        append(value == null ? "" : value.toString(), new SimpleTextAttributes(Font.PLAIN, list.getForeground()));
      }
      setPaintFocusBorder(false);
      setBackground(selected ? UIUtil.getListSelectionBackground() : bgColor);
    }
  }
}
