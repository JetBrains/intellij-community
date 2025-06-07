// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.actions;

import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.actions.generate.DomTemplateRunner;
import com.intellij.util.xml.ui.actions.generate.CreateDomElementAction;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public abstract class CreateClassMappingAction<T extends DomElement> extends CreateDomElementAction<T> {

  private final @Nullable String myBaseClass;
  private final String myTemplate;

  public CreateClassMappingAction(Class<T> contextClass, @Nullable String baseClass, String template) {
    super(contextClass);
    myBaseClass = baseClass;
    myTemplate = template;
  }

  @Override
  protected void createElement(final T context, final Editor editor, PsiFile file, final Project project) {
    PsiClass selectedClass;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      PsiClass baseClass = getBaseClass(context, project, myBaseClass);
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
        .createInheritanceClassChooser(getChooserTitle(), GlobalSearchScope.allScope(project), baseClass, null, new ClassFilter() {
          @Override
          public boolean isAccepted(PsiClass aClass) {
            return !aClass.isInterface() && !aClass.hasModifierProperty(PsiModifier.ABSTRACT);
          }
        });
      chooser.showDialog();
      selectedClass = chooser.getSelected();
    }
    else {
      selectedClass = getBaseClass(context, project, myBaseClass == null ? CommonClassNames.JAVA_LANG_OBJECT : myBaseClass);
    }
    if (selectedClass == null) return;

    createElement(context, editor, file, project, selectedClass);
  }

  protected void createElement(final T context,
                                     final Editor editor,
                                     final PsiFile file,
                                     final Project project,
                                     PsiClass selectedClass) {
    final Map<String, String> map = new HashMap<>();
    map.put("CLASS_NAME", selectedClass.getQualifiedName());
    WriteCommandAction.writeCommandAction(project, file).run(() -> DomTemplateRunner.getInstance(project).runTemplate(createElement(context), myTemplate, editor, map));
  }

  protected @NlsContexts.DialogTitle String getChooserTitle() {
    String text = getTemplatePresentation().getText();
    if (text != null && text.endsWith("...")) {
      text = StringUtil.trimEnd(text, "...");
    }
    return JavaBundle.message("create.class.mapping.dialog.title", text);
  }

  protected abstract DomElement createElement(T context);

  protected @Nullable PsiClass getBaseClass(T context, Project project, String baseClass) {
    return baseClass == null ? null : JavaPsiFacade.getInstance(project).findClass(baseClass, GlobalSearchScope.allScope(project));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
