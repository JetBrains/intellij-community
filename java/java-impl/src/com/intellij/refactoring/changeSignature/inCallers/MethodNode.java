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
package com.intellij.refactoring.changeSignature.inCallers;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author ven
 */
public class MethodNode extends CheckedTreeNode {
  private final PsiMethod myMethod;
  private final Set<PsiMethod> myCalled;
  private final Runnable myCancelCallback;
  private boolean myOldChecked;

  public MethodNode(final PsiMethod method, Set<PsiMethod> called, Runnable cancelCallback) {
    super(method);
    myMethod = method;
    myCalled = called;
    myCancelCallback = cancelCallback;
    isChecked = false;
  }

  public PsiMethod getMethod() {
    return myMethod;
  }

  //IMPORTANT: do not build children in children()
  private void buildChildren () {
    if (children == null) {
      final PsiMethod[] callers = findCallers();
      children = new Vector(callers.length);
      for (PsiMethod caller : callers) {
        final HashSet<PsiMethod> called = new HashSet<PsiMethod>(myCalled);
        called.add(myMethod);
        final MethodNode child = new MethodNode(caller, called, myCancelCallback);
        children.add(child);
        child.parent = this;
      }
    }
  }

  public TreeNode getChildAt(int index) {
    buildChildren();
    return super.getChildAt(index);
  }

  public int getChildCount() {
    buildChildren();
    return super.getChildCount();
  }

  public int getIndex(TreeNode aChild) {
    buildChildren();
    return super.getIndex(aChild);
  }

  private PsiMethod[] findCallers() {
    if (myMethod == null) return PsiMethod.EMPTY_ARRAY;
    final Project project = myMethod.getProject();
    final List<PsiMethod> callers = new ArrayList<PsiMethod>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final PsiReference[] refs =
          MethodReferencesSearch.search(myMethod, GlobalSearchScope.allScope(project), true).toArray(PsiReference.EMPTY_ARRAY);
        for (PsiReference ref : refs) {
          final PsiElement element = ref.getElement();
          if (!(element instanceof PsiReferenceExpression) ||
              !(((PsiReferenceExpression) element).getQualifierExpression() instanceof PsiSuperExpression)) {
            final PsiElement enclosingContext = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiClass.class);
            if (enclosingContext instanceof PsiMethod &&
                !myMethod.equals(enclosingContext) && !myCalled.contains(myMethod)) { //do not add recursive methods
              callers.add((PsiMethod) enclosingContext);
            } else if (element instanceof PsiClass) {
              final PsiClass aClass = (PsiClass)element;
              callers.add(JavaPsiFacade.getElementFactory(project).createMethodFromText(aClass.getName() + "(){}", aClass));
            }
          }
        }
      }
    }, RefactoringBundle.message("caller.chooser.looking.for.callers"), true, project)) {
      myCancelCallback.run();
      return PsiMethod.EMPTY_ARRAY;
    }
    return callers.toArray(new PsiMethod[callers.size()]);
  }

  public void customizeRenderer (ColoredTreeCellRenderer renderer) {
    if (myMethod == null) return;
    int flags = Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS;
    renderer.setIcon(myMethod.getIcon(flags));

    final StringBuffer buffer = new StringBuffer(128);
    final PsiClass containingClass = myMethod.getContainingClass();
    if (containingClass != null) {
      buffer.append(ClassPresentationUtil.getNameForClass(containingClass, false));
      buffer.append('.');
    }
    final String methodText = PsiFormatUtil.formatMethod(
      myMethod,
      PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
      PsiFormatUtil.SHOW_TYPE
    );
    buffer.append(methodText);

    final SimpleTextAttributes attributes = isEnabled() ?
                                            new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getTreeForeground()) :
                                            SimpleTextAttributes.EXCLUDED_ATTRIBUTES;
    renderer.append(buffer.toString(), attributes);

    if (containingClass != null) {
      final String packageName = getPackageName(containingClass);
      renderer.append("  (" + packageName + ")", new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, Color.GRAY));
    }
  }

  @Nullable
  private static String getPackageName(final PsiClass aClass) {
    final PsiFile file = aClass.getContainingFile();
    if (file instanceof PsiJavaFile) {
      return ((PsiJavaFile)file).getPackageName();
    }
    return null;
  }

  public void setEnabled(final boolean enabled) {
    super.setEnabled(enabled);
    if (!enabled) {
      myOldChecked = isChecked();
      setChecked(false);
    }
    else {
      setChecked(myOldChecked);
    }
  }
}
