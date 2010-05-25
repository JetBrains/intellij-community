/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 25-May-2010
 */
package com.intellij.testIntegration;

import com.intellij.codeInsight.navigation.GotoTargetRendererProvider;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;

import javax.swing.*;

public class GotoTestRendererProvider implements GotoTargetRendererProvider {

  public PsiElementListCellRenderer getRenderer(PsiElement[] elements) {
    return new PsiElementListCellRenderer() {
      public String getElementText(final PsiElement element) {
        return element.getContainingFile().getName();
      }

      protected String getContainerText(final PsiElement element, final String name) {
        return null;
      }

      protected int getIconFlags() {
        return 0;
      }

      @Override
      protected String getNullPresentation() {
        return "Create New Test ...";
      }

      @Override
      protected Icon getNullIcon() {
        return IconLoader.getIcon("/actions/intentionBulb.png");
      }

      @Override
      public void installSpeedSearch(PopupChooserBuilder builder) {
        builder.setFilteringEnabled(new Function<Object, String>() {
          public String fun(Object o) {
            if (o instanceof PsiElement) {
              return getElementText((PsiElement)o);
            }
            else {
              return getNullPresentation();
            }
          }
        });
      }


    };
  }
}