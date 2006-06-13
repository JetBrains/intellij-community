/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.ui;

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.ui.UIBundle;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ExtendClass;
import com.intellij.util.xml.GenericDomValue;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author peter
 */
public class PsiClassControl extends EditorTextFieldControl<PsiClassPanel> {

  public PsiClassControl(final DomWrapper<String> domWrapper) {
    super(domWrapper);
  }

  public PsiClassControl(final DomWrapper<String> domWrapper, final boolean commitOnEveryChange) {
    super(domWrapper, commitOnEveryChange);
  }

  protected EditorTextField getEditorTextField(final PsiClassPanel component) {
    return ((ReferenceEditorWithBrowseButton)component.getComponent(0)).getEditorTextField();
  }

  protected PsiClassPanel createMainComponent(PsiClassPanel boundedComponent, final Project project) {
    if (boundedComponent == null) {
      boundedComponent = new PsiClassPanel();
    }
    return initReferenceEditorWithBrowseButton(boundedComponent,
                                               new ReferenceEditorWithBrowseButton(null, "", PsiManager.getInstance(project), true), this);
  }

  protected static <T extends JPanel> T initReferenceEditorWithBrowseButton(final T boundedComponent,
                                                                            final ReferenceEditorWithBrowseButton editor,
                                                                            final EditorTextFieldControl control) {
    boundedComponent.add(editor);
    final GlobalSearchScope resolveScope = control.getDomWrapper().getResolveScope();
    editor.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {

        final DomElement domElement = control.getDomElement();
        ExtendClass extend = domElement.getAnnotation(ExtendClass.class);
        PsiClass baseClass = null;
        TreeClassChooser.ClassFilter filter = null;
        if (extend != null) {
          baseClass = PsiManager.getInstance(control.getProject()).findClass(extend.value(), resolveScope);
          if (extend.instantiatable()) {
            filter = TreeClassChooser.INSTANTIATABLE;
          }
        }

        PsiClass initialClass;
        if (domElement instanceof GenericDomValue) {
          initialClass = (PsiClass)((GenericDomValue)domElement).getValue();
        }
        else {
          initialClass = null;
        }

        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(control.getProject())
          .createInheritanceClassChooser(UIBundle.message("choose.class"), resolveScope, baseClass, initialClass, filter);
        chooser.showDialog();
        final PsiClass psiClass = chooser.getSelectedClass();
        if (psiClass != null) {
          control.setValue(psiClass.getQualifiedName());
        }
      }
    });
    return boundedComponent;
  }

}
