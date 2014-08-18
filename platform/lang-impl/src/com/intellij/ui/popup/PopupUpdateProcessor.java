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

package com.intellij.ui.popup;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiElement;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public abstract class PopupUpdateProcessor extends PopupUpdateProcessorBase {

  private final Project myProject;

  protected PopupUpdateProcessor(Project project) {
    myProject = project;
  }

  @Override
  public void beforeShown(final LightweightWindowEvent windowEvent) {
    final Lookup activeLookup = LookupManager.getInstance(myProject).getActiveLookup();
    if (activeLookup != null) {
      activeLookup.addLookupListener(new LookupAdapter() {
        @Override
        public void currentItemChanged(LookupEvent event) {
          if (windowEvent.asPopup().isVisible()) { //was not canceled yet
            final LookupElement item = event.getItem();
            if (item != null) {
              PsiElement targetElement = CompletionUtil.getTargetElement(item);
              if (targetElement == null) {
                targetElement = DocumentationManager.getInstance(myProject).getElementFromLookup(activeLookup.getEditor(), activeLookup.getPsiFile());
              }

              updatePopup(targetElement); //open next
            }
          } else {
            activeLookup.removeLookupListener(this);
          }
        }
      });
    }
    else {
      final Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(myProject);
      boolean fromQuickSearch = focusedComponent != null && focusedComponent.getParent() instanceof ChooseByNameBase.JPanelProvider;
      if (fromQuickSearch) {
        ChooseByNameBase.JPanelProvider panelProvider = (ChooseByNameBase.JPanelProvider)focusedComponent.getParent();
        panelProvider.registerHint(windowEvent.asPopup());
      }
      else if (focusedComponent instanceof JComponent) {
        HintUpdateSupply supply = HintUpdateSupply.getSupply((JComponent)focusedComponent);
        if (supply != null) supply.registerHint(windowEvent.asPopup());
      }
    }
  }
}
