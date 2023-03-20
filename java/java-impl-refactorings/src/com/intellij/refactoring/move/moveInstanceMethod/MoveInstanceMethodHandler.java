// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiTypesUtil;
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

public class MoveInstanceMethodHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance(MoveInstanceMethodHandler.class);

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (element == null) {
      element = file.findElementAt(editor.getCaretModel().getOffset());
    }

    if (element == null) return;
    if (element instanceof PsiIdentifier) element = element.getParent();

    if (!(element instanceof PsiMethod)) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("error.wrong.caret.position.method"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.MOVE_INSTANCE_METHOD);
      return;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Move Instance Method invoked");
    }
    invoke(project, new PsiElement[]{element}, dataContext);
  }

  @Override
  public void invoke(@NotNull final Project project, final PsiElement @NotNull [] elements, final DataContext dataContext) {
    if (elements.length != 1 || !(elements[0] instanceof PsiMethod method)) return;
    String message = null;
    if (!method.getManager().isInProject(method)) {
      message = JavaRefactoringBundle.message("move.method.is.not.supported.for.non.project.methods");
    } else if (method.isConstructor()) {
      message = JavaRefactoringBundle.message("move.method.is.not.supported.for.constructors");
    } else if (method.getLanguage()!= JavaLanguage.INSTANCE) {
      message = JavaRefactoringBundle.message("move.method.is.not.supported.for.0", method.getLanguage().getDisplayName());
    }
    else {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && mentionTypeParameters(method)) {
        message = JavaRefactoringBundle.message("move.method.is.not.supported.for.generic.classes");
      }
      else if (method.findSuperMethods().length > 0 ||
               OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY).length > 0) {
        message = RefactoringBundle.message("move.method.is.not.supported.when.method.is.part.of.inheritance.hierarchy");
      }
      else {
        final Set<PsiClass> classes = MoveInstanceMembersUtil.getThisClassesToMembers(method).keySet();
        for (PsiClass aClass : classes) {
          if (aClass instanceof JspClass) {
            message = JavaRefactoringBundle.message("synthetic.jsp.class.is.referenced.in.the.method");
            Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
            CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.MOVE_INSTANCE_METHOD);
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
        final String suggestToMakeStaticMessage =
          JavaRefactoringBundle.message("move.instance.method.handler.make.method.static", method.getName());
        if (Messages
          .showYesNoCancelDialog(project, message + ". " + suggestToMakeStaticMessage,
                                 getRefactoringName(), Messages.getErrorIcon()) == Messages.YES) {
          MakeStaticHandler.invoke(method);
        }
      }
      return;
    }

    new MoveInstanceMethodDialog(
      method,
      suitableVariables.toArray(new PsiVariable[0])).show();
  }

  private static void showErrorHint(Project project, DataContext dataContext, @NlsContexts.DialogMessage String message) {
    Editor editor = dataContext == null ? null : CommonDataKeys.EDITOR.getData(dataContext);
    CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(message), getRefactoringName(), HelpID.MOVE_INSTANCE_METHOD);
  }

  @Nullable
  private static @NlsContexts.DialogMessage String collectSuitableVariables(final PsiMethod method, final List<? super PsiVariable> suitableVariables) {
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
        return JavaRefactoringBundle.message("there.are.no.variables.that.have.reference.type");
      }
      else if (!resolvableClassesFound) {
        return JavaRefactoringBundle.message("all.candidate.variables.have.unknown.types");
      }
      else if (!classesInProjectFound) {
        return JavaRefactoringBundle.message("all.candidate.variables.have.types.not.in.project");
      }
    }
    return null;
  }

  public static String suggestParameterNameForThisClass(final PsiClass thisClass) {
    PsiManager manager = thisClass.getManager();
    PsiType type = JavaPsiFacade.getElementFactory(manager.getProject()).createType(thisClass);
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

  private static boolean mentionTypeParameters(PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;
    Set<PsiTypeParameter> typeParameters = ContainerUtil.newHashSet(PsiUtil.typeParametersIterable(containingClass));
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      if (PsiTypesUtil.mentionsTypeParameters(parameter.getType(), typeParameters)) return true;
    }
    return PsiTypesUtil.mentionsTypeParameters(method.getReturnType(), typeParameters);
  }

  static @NlsContexts.DialogTitle String getRefactoringName() {
    return RefactoringBundle.message("move.instance.method.title");
  }
}
