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
package com.intellij.util.xml.ui;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiType;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JavaReferenceEditorUtil;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.AbstractConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.JvmPsiTypeConverterImpl;
import org.jetbrains.annotations.NotNull;

public class PsiTypeControl extends EditorTextFieldControl<PsiTypePanel> {

  public PsiTypeControl(final DomWrapper<String> domWrapper, final boolean commitOnEveryChange) {
    super(domWrapper, commitOnEveryChange);
  }

  @Override
  @NotNull
  protected String getValue() {
    final String rawValue = super.getValue();
    try {
      final PsiType psiType = JavaPsiFacade.getElementFactory(getProject()).createTypeFromText(rawValue, null);
      final String s = JvmPsiTypeConverterImpl.convertToString(psiType);
      if (s != null) {
        return s;
      }
    }
    catch (IncorrectOperationException ignored) {
    }
    return rawValue;
  }

  @Override
  protected void setValue(String value) {
    final PsiType type = JvmPsiTypeConverterImpl.convertFromString(value, new AbstractConvertContext() {
      @Override
      @NotNull
      public DomElement getInvocationElement() {
        return getDomElement();
      }
    });
    if (type != null) {
      value = type.getCanonicalText();
    }
    super.setValue(value);
  }

  @Override
  protected EditorTextField getEditorTextField(@NotNull final PsiTypePanel component) {
    return ((ReferenceEditorWithBrowseButton)component.getComponent(0)).getEditorTextField();
  }

  @Override
  protected PsiTypePanel createMainComponent(PsiTypePanel boundedComponent, final Project project) {
    if (boundedComponent == null) {
      boundedComponent = new PsiTypePanel();
    }
    return PsiClassControl.initReferenceEditorWithBrowseButton(boundedComponent,
                                                               new ReferenceEditorWithBrowseButton(null, project,
                                                                                                   s -> JavaReferenceEditorUtil.createTypeDocument(s, project), ""), this);
  }


}
