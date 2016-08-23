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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class CreateInnerClassFromUsageFix extends CreateClassFromUsageBaseFix {

  public CreateInnerClassFromUsageFix(final PsiJavaCodeReferenceElement refElement, final CreateClassKind kind) {
    super(kind, refElement);
  }

  @Override
  public String getText(String varName) {
    return QuickFixBundle.message("create.inner.class.from.usage.text", myKind.getDescription(), varName);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final PsiJavaCodeReferenceElement element = getRefElement();
    if (element == null) return;
    final String superClassName = getSuperClassName(element);
    PsiClass[] targets = getPossibleTargets(element);
    LOG.assertTrue(targets.length > 0);
    if (targets.length == 1) {
      doInvoke(targets[0], superClassName);
    }
    else {
      chooseTargetClass(targets, editor, superClassName);
    }
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return super.isAvailable(project, editor, file) && getPossibleTargets(getRefElement()).length > 0;
  }

  @NotNull
  private static PsiClass[] getPossibleTargets(final PsiJavaCodeReferenceElement element) {
    List<PsiClass> result = new ArrayList<>();
    PsiElement run = element;
    PsiMember contextMember = PsiTreeUtil.getParentOfType(run, PsiMember.class);

    while (contextMember != null) {
      if (contextMember instanceof PsiClass && !(contextMember instanceof PsiTypeParameter)) {
        if (!isUsedInExtends(run, (PsiClass)contextMember)) {
          result.add((PsiClass)contextMember);
        }
      }
      run = contextMember;
      contextMember = PsiTreeUtil.getParentOfType(run, PsiMember.class);
    }

    return result.isEmpty() ? PsiClass.EMPTY_ARRAY : result.toArray(new PsiClass[result.size()]);
  }

  private static boolean isUsedInExtends(PsiElement element, PsiClass psiClass) {
    final PsiReferenceList extendsList = psiClass.getExtendsList();
    final PsiReferenceList implementsList = psiClass.getImplementsList();
    if (extendsList != null && PsiTreeUtil.isAncestor(extendsList, element, false)) {
      return true;
    }

    return implementsList != null && PsiTreeUtil.isAncestor(implementsList, element, false);
  }

  private void chooseTargetClass(PsiClass[] classes, final Editor editor, final String superClassName) {
    final Project project = classes[0].getProject();

    final JList list = new JBList(classes);
    PsiElementListCellRenderer renderer = PsiClassListCellRenderer.INSTANCE;
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(renderer);
    final PopupChooserBuilder builder = new PopupChooserBuilder(list);
    renderer.installSpeedSearch(builder);

    Runnable runnable = () -> {
      int index = list.getSelectedIndex();
      if (index < 0) return;
      final PsiClass aClass = (PsiClass)list.getSelectedValue();
      CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          doInvoke(aClass, superClassName);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }), getText(), null);
    };

    builder.
      setTitle(QuickFixBundle.message("target.class.chooser.title")).
      setItemChoosenCallback(runnable).
      createPopup().
      showInBestPositionFor(editor);
  }

  private void doInvoke(final PsiClass aClass, final String superClassName) throws IncorrectOperationException {
    PsiJavaCodeReferenceElement ref = getRefElement();
    assert ref != null;
    String refName = ref.getReferenceName();
    LOG.assertTrue(refName != null);
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
    PsiClass created = myKind == CreateClassKind.INTERFACE
                      ? elementFactory.createInterface(refName)
                      : myKind == CreateClassKind.CLASS ? elementFactory.createClass(refName) : elementFactory.createEnum(refName);
    final PsiModifierList modifierList = created.getModifierList();
    LOG.assertTrue(modifierList != null);
    if (aClass.isInterface()) {
      modifierList.setModifierProperty(PsiModifier.PACKAGE_LOCAL, true);
    } else {
      modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
    }
    if (RefactoringUtil.isInStaticContext(ref, aClass)) {
      modifierList.setModifierProperty(PsiModifier.STATIC, true);
    }
    if (superClassName != null) {
      PsiJavaCodeReferenceElement superClass =
        elementFactory.createReferenceElementByFQClassName(superClassName, created.getResolveScope());
      final PsiReferenceList extendsList = created.getExtendsList();
      LOG.assertTrue(extendsList != null);
      extendsList.add(superClass);
    }
    CreateFromUsageBaseFix.setupGenericParameters(created, ref);

    created = (PsiClass)aClass.add(created);
    ref.bindToElement(created);
  }
}
