/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.AbstractConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.JvmPsiTypeConverterImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiTypeControl extends EditorTextFieldControl<PsiTypePanel> {

  public PsiTypeControl(final DomWrapper<String> domWrapper, final boolean commitOnEveryChange) {
    super(domWrapper, commitOnEveryChange);
  }

  @NotNull
  protected String getValue() {
    final String rawValue = super.getValue();
    try {
      final PsiType psiType = JavaPsiFacade.getInstance(getProject()).getElementFactory().createTypeFromText(rawValue, null);
      final String s = JvmPsiTypeConverterImpl.convertToString(psiType);
      if (s != null) {
        return s;
      }
    }
    catch (IncorrectOperationException e) {
    }
    return rawValue;
  }

  private PsiManager getPsiManager() {
    return PsiManager.getInstance(getProject());
  }

  protected void setValue(String value) {
    final PsiType type = JvmPsiTypeConverterImpl.convertFromString(value, new AbstractConvertContext() {
      @NotNull
      public DomElement getInvocationElement() {
        return getDomElement();
      }

      public PsiManager getPsiManager() {
        return PsiTypeControl.this.getPsiManager();
      }
    });
    if (type != null) {
      value = type.getCanonicalText();
    }
    super.setValue(value);
  }

  protected EditorTextField getEditorTextField(@NotNull final PsiTypePanel component) {
    return ((ReferenceEditorWithBrowseButton)component.getComponent(0)).getEditorTextField();
  }

  protected PsiTypePanel createMainComponent(PsiTypePanel boundedComponent, final Project project) {
    if (boundedComponent == null) {
      boundedComponent = new PsiTypePanel();
    }
    return PsiClassControl.initReferenceEditorWithBrowseButton(boundedComponent,
                                                                new ReferenceEditorWithBrowseButton(null, project, new Function<String, Document>() {
                                                                  public Document fun(final String s) {
                                                                    return ReferenceEditorWithBrowseButton.createTypeDocument(s, PsiManager.getInstance(project));
                                                                  }
                                                                }, ""), this);
  }


}
