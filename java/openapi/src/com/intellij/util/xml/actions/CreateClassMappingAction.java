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
package com.intellij.util.xml.actions;

import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.actions.generate.DomTemplateRunner;
import com.intellij.util.xml.ui.actions.generate.CreateDomElementAction;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public abstract class CreateClassMappingAction<T extends DomElement> extends CreateDomElementAction<T> {

  @Nullable private final String myBaseClass;
  private final String myTemplate;

  public CreateClassMappingAction(Class<T> contextClass, @Nullable String baseClass, String template) {
    super(contextClass);
    myBaseClass = baseClass;
    myTemplate = template;
  }

  @Override
  protected DomElement createElement(final T context, final Editor editor, PsiFile file, final Project project) {
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
    if (selectedClass == null) return null;

    return createElement(context, editor, file, project, selectedClass);
  }

  @Nullable
  protected DomElement createElement(final T context,
                                     final Editor editor,
                                     final PsiFile file,
                                     final Project project,
                                     PsiClass selectedClass) {
    final Map<String,String> map = new HashMap<>();
    map.put("CLASS_NAME", selectedClass.getQualifiedName());
    new WriteCommandAction.Simple(project, file) {
      @Override
      protected void run() throws Throwable {
        DomTemplateRunner.getInstance(project).runTemplate(createElement(context), myTemplate, editor, map);
      }
    }.execute();
    return null;
  }

  protected String getChooserTitle() {
    String text = getTemplatePresentation().getText();
    if (text.endsWith("...")) {
      text = StringUtil.trimEnd(text, "...");
    }
    return "Choose " + text + " Class";
  }

  protected abstract DomElement createElement(T context);

  @Nullable
  protected PsiClass getBaseClass(T context, Project project, String baseClass) {
    return baseClass == null ? null : JavaPsiFacade.getInstance(project).findClass(baseClass, GlobalSearchScope.allScope(project));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
