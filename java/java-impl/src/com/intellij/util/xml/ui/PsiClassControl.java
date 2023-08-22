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

import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.IntentionFilterOwner;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.PsiCodeFragmentImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JavaReferenceEditorUtil;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ExtendClass;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PsiClassControl extends EditorTextFieldControl<PsiClassPanel> {

  public PsiClassControl(final DomWrapper<String> domWrapper) {
    super(domWrapper);
  }

  public PsiClassControl(final DomWrapper<String> domWrapper, final boolean commitOnEveryChange) {
    super(domWrapper, commitOnEveryChange);
  }

  @Override
  protected EditorTextField getEditorTextField(@NotNull final PsiClassPanel component) {
    return ((ReferenceEditorWithBrowseButton)component.getComponent(0)).getEditorTextField();
  }

  @Override
  protected PsiClassPanel createMainComponent(PsiClassPanel boundedComponent, final Project project) {
    if (boundedComponent == null) {
      boundedComponent = new PsiClassPanel();
    }
    ReferenceEditorWithBrowseButton editor = JavaReferenceEditorUtil.createReferenceEditorWithBrowseButton(null, "", project, true);
    Document document = editor.getChildComponent().getDocument();
    PsiCodeFragmentImpl fragment = (PsiCodeFragmentImpl) PsiDocumentManager.getInstance(project).getPsiFile(document);
    assert fragment != null;
    fragment.setIntentionActionsFilter(IntentionFilterOwner.IntentionActionsFilter.EVERYTHING_AVAILABLE);
    fragment.putUserData(ModuleUtil.KEY_MODULE, getDomWrapper().getExistingDomElement().getModule());
    return initReferenceEditorWithBrowseButton(boundedComponent, editor, this);
  }

  protected static <T extends JPanel> T initReferenceEditorWithBrowseButton(final T boundedComponent,
                                                                            final ReferenceEditorWithBrowseButton editor,
                                                                            final EditorTextFieldControl control) {
    boundedComponent.removeAll();
    boundedComponent.add(editor);
    final GlobalSearchScope resolveScope = control.getDomWrapper().getResolveScope();
    editor.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {

        final DomElement domElement = control.getDomElement();
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
        if (domElement instanceof GenericDomValue) {
          final Object value = ((GenericDomValue<?>)domElement).getValue();
          if (value instanceof PsiClass)
            initialClass = (PsiClass)value;
        }

        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(control.getProject())
          .createInheritanceClassChooser(JavaBundle.message("choose.class"), resolveScope, baseClass, initialClass, filter);
        chooser.showDialog();
        final PsiClass psiClass = chooser.getSelected();
        if (psiClass != null) {
          control.setValue(psiClass.getQualifiedName());
        }
      }
    });
    return boundedComponent;
  }

}
