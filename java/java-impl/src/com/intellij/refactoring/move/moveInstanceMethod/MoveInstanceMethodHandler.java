/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.makeStatic.MakeStaticHandler;
import com.intellij.refactoring.move.MoveInstanceMembersUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author ven
 */
public class MoveInstanceMethodHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodHandler");
  static final String REFACTORING_NAME = RefactoringBundle.message("move.instance.method.title");

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (element == null) {
      element = file.findElementAt(editor.getCaretModel().getOffset());
    }

    if (element == null) return;
    if (element instanceof PsiIdentifier) element = element.getParent();

    if (!(element instanceof PsiMethod)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.method"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.MOVE_INSTANCE_METHOD);
      return;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Move Instance Method invoked");
    }
    invoke(project, new PsiElement[]{element}, dataContext);
  }

  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, final DataContext dataContext) {
    if (elements.length != 1 || !(elements[0] instanceof PsiMethod)) return;
    final PsiMethod method = (PsiMethod)elements[0];
    String message = null;
    if (!method.getManager().isInProject(method)) {
      message = "Move method is not supported for non-project methods";
    } else if (method.isConstructor()) {
      message = RefactoringBundle.message("move.method.is.not.supported.for.constructors");
    } else if (method.getLanguage()!= JavaLanguage.INSTANCE) {
      message = RefactoringBundle.message("move.method.is.not.supported.for.0", method.getLanguage().getDisplayName());
    }
    else {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && PsiUtil.typeParametersIterator(containingClass).hasNext() && TypeParametersSearcher.hasTypeParameters(method)) {
        message = RefactoringBundle.message("move.method.is.not.supported.for.generic.classes");
      }
      else if (method.findSuperMethods().length > 0 ||
               OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY).length > 0) {
        message = RefactoringBundle.message("move.method.is.not.supported.when.method.is.part.of.inheritance.hierarchy");
      }
      else {
        final Set<PsiClass> classes = MoveInstanceMembersUtil.getThisClassesToMembers(method).keySet();
        for (PsiClass aClass : classes) {
          if (aClass instanceof JspClass) {
            message = RefactoringBundle.message("synthetic.jsp.class.is.referenced.in.the.method");
            Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
            CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.MOVE_INSTANCE_METHOD);
            break;
          }
        }
      }
    }
    if (message != null) {
      showErrorHint(project, dataContext, message);
      return;
    }

    final List<PsiVariable> suitableVariables = new ArrayList<>();
    message = collectSuitableVariables(method, suitableVariables);
    if (message != null) {
      final String unableToMakeStaticMessage = MakeStaticHandler.validateTarget(method);
      if (unableToMakeStaticMessage != null) {
        showErrorHint(project, dataContext, message);
      }
      else {
        final String suggestToMakeStaticMessage = "Would you like to make method \'" + method.getName() + "\' static and then move?";
        if (Messages
          .showYesNoCancelDialog(project, message + ". " + suggestToMakeStaticMessage,
                                 REFACTORING_NAME, Messages.getErrorIcon()) == Messages.YES) {
          MakeStaticHandler.invoke(method);
        }
      }
      return;
    }

    new MoveInstanceMethodDialog(
      method,
      suitableVariables.toArray(new PsiVariable[suitableVariables.size()])).show();
  }

  private static void showErrorHint(Project project, DataContext dataContext, String message) {
    Editor editor = dataContext == null ? null : CommonDataKeys.EDITOR.getData(dataContext);
    CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(message), REFACTORING_NAME, HelpID.MOVE_INSTANCE_METHOD);
  }

  @Nullable
  private static String collectSuitableVariables(final PsiMethod method, final List<PsiVariable> suitableVariables) {
    final List<PsiVariable> allVariables = new ArrayList<>();
    ContainerUtil.addAll(allVariables, method.getParameterList().getParameters());
    ContainerUtil.addAll(allVariables, method.getContainingClass().getFields());
    boolean classTypesFound = false;
    boolean resolvableClassesFound = false;
    boolean classesInProjectFound = false;
    for (PsiVariable variable : allVariables) {
      final PsiType type = variable.getType();
      if (type instanceof PsiClassType && !((PsiClassType)type).hasParameters()) {
        classTypesFound = true;
        final PsiClass psiClass = ((PsiClassType)type).resolve();
        if (psiClass != null && !(psiClass instanceof PsiTypeParameter)) {
          resolvableClassesFound = true;
          final boolean inProject = method.getManager().isInProject(psiClass);
          if (inProject) {
            classesInProjectFound = true;
            suitableVariables.add(variable);
          }
        }
      }
    }

    if (suitableVariables.isEmpty()) {
      if (!classTypesFound) {
        return RefactoringBundle.message("there.are.no.variables.that.have.reference.type");
      }
      else if (!resolvableClassesFound) {
        return RefactoringBundle.message("all.candidate.variables.have.unknown.types");
      }
      else if (!classesInProjectFound) {
        return RefactoringBundle.message("all.candidate.variables.have.types.not.in.project");
      }
    }
    return null;
  }

  public static String suggestParameterNameForThisClass(final PsiClass thisClass) {
    PsiManager manager = thisClass.getManager();
    PsiType type = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createType(thisClass);
    final SuggestedNameInfo suggestedNameInfo = JavaCodeStyleManager.getInstance(manager.getProject())
      .suggestVariableName(VariableKind.PARAMETER, null, null, type);
    return suggestedNameInfo.names.length > 0 ? suggestedNameInfo.names[0] : "";
  }

  public static Map<PsiClass, String> suggestParameterNames(final PsiMethod method, final PsiVariable targetVariable) {
    final Map<PsiClass, Set<PsiMember>> classesToMembers = MoveInstanceMembersUtil.getThisClassesToMembers(method);
    Map<PsiClass, String> result = new LinkedHashMap<>();
    for (Map.Entry<PsiClass, Set<PsiMember>> entry : classesToMembers.entrySet()) {
      PsiClass aClass = entry.getKey();
      final Set<PsiMember> members = entry.getValue();
      if (members.size() == 1 && members.contains(targetVariable)) continue;
      result.put(aClass, suggestParameterNameForThisClass(aClass));
    }
    return result;
  }

  private static class TypeParametersSearcher extends PsiTypeVisitor<Boolean> {
    public static boolean hasTypeParameters(PsiElement element) {
      final TypeParametersSearcher searcher = new TypeParametersSearcher();
      final boolean[] hasParameters = new boolean[]{false};
      element.accept(new JavaRecursiveElementWalkingVisitor(){
        @Override
        public void visitTypeElement(PsiTypeElement type) {
          super.visitTypeElement(type);
          hasParameters[0] |= type.getType().accept(searcher);
        }
      });
      return hasParameters[0];
    }

    @Override
    public Boolean visitClassType(PsiClassType classType) {
      final PsiClass psiClass = PsiUtil.resolveClassInType(classType);
      if (psiClass instanceof PsiTypeParameter) {
        return Boolean.TRUE;
      }
      return super.visitClassType(classType);
    }

    @Override
    public Boolean visitWildcardType(PsiWildcardType wildcardType) {
      final PsiType bound = wildcardType.getBound();
      if (PsiUtil.resolveClassInType(bound) instanceof PsiTypeParameter) {
        return Boolean.TRUE;
      }
      return super.visitWildcardType(wildcardType);
    }

    @Override
    public Boolean visitType(PsiType type) {
      return Boolean.FALSE;
    }
  }
}
