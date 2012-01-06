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
package com.intellij.ui;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ElementColorProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.FunctionUtil;
import com.intellij.util.ui.ColorIcon;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ColorLineMarkerProvider implements LineMarkerProvider {
  @Override
  public LineMarkerInfo getLineMarkerInfo(PsiElement element) {    
    for (ElementColorProvider colorProvider : ElementColorProvider.EP_NAME.getExtensions()) {
      final Color color = colorProvider.getColorFrom(element);
      if (color != null) {
        return new MyInfo(element, color, colorProvider);
      }
    }
    return null;
  }

  @Override
  public void collectSlowLineMarkers(List<PsiElement> elements, Collection<LineMarkerInfo> result) {
  }
  
  private static class MyInfo extends LineMarkerInfo<PsiElement> {

    public MyInfo(@NotNull final PsiElement element, final Color color, final ElementColorProvider colorProvider) {
      super(element, 
            element.getTextRange(), 
            new ColorIcon(12, color), 
            Pass.UPDATE_ALL, 
            FunctionUtil.<Object, String>nullConstant(), 
            new GutterIconNavigationHandler<PsiElement>() {
              @Override
              public void navigate(MouseEvent e, PsiElement elt) {
                final Editor editor = PsiUtilBase.findEditor(element);
                assert editor != null;
                final Color c = ColorChooser.chooseColor(editor.getComponent(), "Choose color", color, true);
                if (c != null) {
                  colorProvider.setColorTo(element, c);
                }
              }
            }, 
            GutterIconRenderer.Alignment.RIGHT);
    }
  }
}
