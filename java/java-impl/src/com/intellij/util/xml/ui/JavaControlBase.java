// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.ui;

import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ExtendClass;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;

@ApiStatus.Internal
public abstract class JavaControlBase<T extends JComponent> extends EditorTextFieldControl<T> {
  protected JavaControlBase(DomWrapper<String> domWrapper, boolean commitOnEveryChange) {
    super(domWrapper, commitOnEveryChange);
  }

  protected void setValue(PsiClass value) {
    super.setValue(value.getQualifiedName());
  }

  protected static <T extends JPanel> T initReferenceEditorWithBrowseButton(
    T boundedComponent,
    ReferenceEditorWithBrowseButton editor,
    JavaControlBase<?> control
  ) {
    boundedComponent.removeAll();
    boundedComponent.add(editor);
    GlobalSearchScope resolveScope = control.getDomWrapper().getResolveScope();
    editor.addActionListener(e -> {
      DomElement domElement = control.getDomElement();
      ExtendClass extend = domElement.getAnnotation(ExtendClass.class);
      PsiClass baseClass = null;
      ClassFilter filter = null;
      if (extend != null) {
        if (extend.value().length == 1) baseClass = JavaPsiFacade.getInstance(control.getProject()).findClass(extend.value()[0], resolveScope);
        if (extend.instantiatable()) {
          filter = ClassFilter.INSTANTIABLE;
        }
      }

      PsiClass initialClass = null;
      if (domElement instanceof GenericDomValue && ((GenericDomValue<?>)domElement).getValue() instanceof PsiClass psiClass) {
        initialClass = psiClass;
      }

      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(control.getProject())
        .createInheritanceClassChooser(JavaBundle.message("choose.class"), resolveScope, baseClass, initialClass, filter);
      chooser.showDialog();
      PsiClass psiClass = chooser.getSelected();
      if (psiClass != null) {
        control.setValue(psiClass);
      }
    });
    return boundedComponent;
  }
}
