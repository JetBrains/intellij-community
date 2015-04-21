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
package com.intellij.refactoring.ui;

import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author yole
 */
public class ClassNameReferenceEditor extends ReferenceEditorWithBrowseButton {
  public static final Key<Boolean> CLASS_NAME_REFERENCE_FRAGMENT = Key.create("CLASS_NAME_REFERENCE_FRAGMENT");
  private final Project myProject;
  private PsiClass mySelectedClass;
  private String myChooserTitle;

  public ClassNameReferenceEditor(@NotNull final Project project, @Nullable final PsiClass selectedClass) {
    this(project, selectedClass, null);
  }

  public ClassNameReferenceEditor(@NotNull final Project project, @Nullable final PsiClass selectedClass,
                                  @Nullable final GlobalSearchScope resolveScope) {
    super(null, project, new Function<String,Document>() {
      public Document fun(final String s) {
        PsiPackage defaultPackage = JavaPsiFacade.getInstance(project).findPackage("");
        final JavaCodeFragment fragment = JavaCodeFragmentFactory.getInstance(project).createReferenceCodeFragment(s, defaultPackage, true, true);
        fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
        if (resolveScope != null) {
          fragment.forceResolveScope(resolveScope);
        }
        fragment.putUserData(CLASS_NAME_REFERENCE_FRAGMENT, true);
        return PsiDocumentManager.getInstance(project).getDocument(fragment);
      }
    }, selectedClass != null ? selectedClass.getQualifiedName() : "");

    myProject = project;
    myChooserTitle = "Choose Class";
    addActionListener(new ChooseClassAction());
  }

  public String getChooserTitle() {
    return myChooserTitle;
  }

  public void setChooserTitle(final String chooserTitle) {
    myChooserTitle = chooserTitle;
  }

  private class ChooseClassAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createWithInnerClassesScopeChooser(myChooserTitle,
                                                                                                                   GlobalSearchScope.projectScope(myProject),
                                                                                                                   new ClassFilter() {
        public boolean isAccepted(PsiClass aClass) {
          return aClass.getParent() instanceof PsiJavaFile || aClass.hasModifierProperty(PsiModifier.STATIC);
        }
      }, null);
      if (mySelectedClass != null) {
        chooser.selectDirectory(mySelectedClass.getContainingFile().getContainingDirectory());
      }
      chooser.showDialog();
      mySelectedClass = chooser.getSelected();
      if (mySelectedClass != null) {
        setText(mySelectedClass.getQualifiedName());
      }
    }
  }
}