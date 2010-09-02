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

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
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

  private final String myBaseClass;
  private final String myTemplate;

  public CreateClassMappingAction(Class<T> contextClass, String baseClass, String template) {
    super(contextClass);
    myBaseClass = baseClass;
    myTemplate = template;
  }

  @Override
  protected DomElement createElement(final T context, final Editor editor, PsiFile file, final Project project) {
    PsiClass selectedClass;
    PsiClass baseClass = getBaseClass(context, project);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
        .createInheritanceClassChooser(getChooserTitle(), GlobalSearchScope.allScope(project), baseClass, null, new TreeClassChooser.ClassFilter() {
          @Override
          public boolean isAccepted(PsiClass aClass) {
            return !aClass.isInterface() && !aClass.hasModifierProperty(PsiModifier.ABSTRACT);
          }
        });
      chooser.showDialog();
      selectedClass = chooser.getSelectedClass();
    }
    else {
      selectedClass = baseClass;
    }
    if (selectedClass == null) return null;

    final Map<String,String> map = new HashMap<String, String>();
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
    return "Choose " + getTemplatePresentation().getTextWithMnemonic() + " Class";
  }

  protected abstract DomElement createElement(T context);

  @Nullable
  protected PsiClass getBaseClass(T context, Project project) {
    return JavaPsiFacade.getInstance(project).findClass(myBaseClass, GlobalSearchScope.allScope(project));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
