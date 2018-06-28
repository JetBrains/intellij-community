// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

public class CreateTypeParameterFromUsageFix extends BaseIntentionAction {
  private final SmartPsiElementPointer<PsiJavaCodeReferenceElement> myRef;

  public CreateTypeParameterFromUsageFix(PsiJavaCodeReferenceElement refElement) {
    myRef = SmartPointerManager.getInstance(refElement.getProject()).createSmartPsiElementPointer(refElement);
  }

  @Nullable
  private PsiJavaCodeReferenceElement getElement() {
    return myRef.getElement();
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("create.type.parameter.from.usage.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiJavaCodeReferenceElement element = getElement();
    if (element == null) return false;
    Context context = Context.from(element, true);
    boolean available = context != null;
    if (available) {
      setText(QuickFixBundle.message("create.type.parameter.from.usage.text", context.typeName));
    }
    return available;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiJavaCodeReferenceElement element = getElement();
    if (element == null) return;
    Context context = Context.from(element, false);
    if (context == null) return;
    List<PsiNameIdentifierOwner> placesToAdd = context.myPlacesToAdd;

    Application application = ApplicationManager.getApplication();
    if (placesToAdd.size() == 1 || application.isUnitTestMode() || editor == null) {
      PsiElement first = placesToAdd.get(0);
      createTypeParameter(first, context.typeName);
    }
    else {
      IntroduceTargetChooser.showChooser(
        editor,
        placesToAdd,
        new Pass<PsiNameIdentifierOwner>() {
          @Override
          public void pass(PsiNameIdentifierOwner owner) {
            createTypeParameter(owner, context.typeName);
          }
        },
        PsiNamedElement::getName,
        QuickFixBundle.message("create.type.parameter.from.usage.chooser.title")
      );
    }
  }


  private static void createTypeParameter(@NotNull PsiElement methodOrClass, @NotNull String name) {
    Project project = methodOrClass.getProject();
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(methodOrClass)) return;
    WriteCommandAction.runWriteCommandAction(project, () -> {
      PsiTypeParameterListOwner typeParameterListOwner = tryCast(methodOrClass, PsiTypeParameterListOwner.class);
      if (typeParameterListOwner == null) {
        throw new IllegalStateException("Only methods and classes allowed here, but was: " + methodOrClass.getClass());
      }
      PsiTypeParameterList typeParameterList = typeParameterListOwner.getTypeParameterList();
      final String typeParameterListText;
      if (typeParameterList == null) {
        typeParameterListText = "<" + name + ">";
      }
      else {
        String existingTypeParameterText = typeParameterList.getText();
        if (typeParameterList.getTypeParameters().length == 0) {
          typeParameterListText = "<" + name + ">";
        }
        else {
          String prefix = existingTypeParameterText.substring(0, existingTypeParameterText.length() - 1);
          typeParameterListText = prefix + ", " + name + ">";
        }
      }
      PsiTypeParameterList newTypeParameterList = createTypeParameterList(typeParameterListText, project);
      replaceOrAddTypeParameterList(methodOrClass, typeParameterList, newTypeParameterList);
    });
  }

  private static void replaceOrAddTypeParameterList(@NotNull PsiElement methodOrClass,
                                                    @Nullable PsiTypeParameterList typeParameterList,
                                                    @NotNull PsiTypeParameterList newTypeParameterList) {
    if (methodOrClass instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)methodOrClass;
      if (typeParameterList == null) {
        PsiTypeElement returnTypeElement = method.getReturnTypeElement();
        if (returnTypeElement == null) return;
        method.addBefore(newTypeParameterList, returnTypeElement);
      }
      else {
        typeParameterList.replace(newTypeParameterList);
      }
    }
    else {
      PsiClass aClass = (PsiClass)methodOrClass;
      if (typeParameterList == null) {
        PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
        if (nameIdentifier == null) return;
        aClass.addAfter(newTypeParameterList, nameIdentifier);
      }
      else {
        typeParameterList.replace(newTypeParameterList);
      }
    }
  }

  private static PsiTypeParameterList createTypeParameterList(@NotNull String text, Project project) {
    PsiJavaFile javaFile = (PsiJavaFile)PsiFileFactory.getInstance(project)
                                                      .createFileFromText("_DUMMY_", JavaFileType.INSTANCE,
                                                                          "class __DUMMY__ " + text + " {}");
    PsiClass[] classes = javaFile.getClasses();
    return classes[0].getTypeParameterList();
  }

  private static class Context {
    @NotNull final List<PsiNameIdentifierOwner> myPlacesToAdd;
    @NotNull final String typeName;

    Context(@NotNull List<PsiNameIdentifierOwner> add, @NotNull String name) {
      myPlacesToAdd = add;
      typeName = name;
    }

    @Nullable
    static Context from(@NotNull PsiJavaCodeReferenceElement element, boolean findFirstOnly) {
      if (!PsiUtil.isLanguageLevel5OrHigher(element)) return null;
      if (element.isQualified()) return null;
      PsiElement parent = element.getParent();
      if (parent instanceof PsiMethodCallExpression ||
          parent instanceof PsiJavaCodeReferenceElement ||
          parent instanceof PsiNewExpression ||
          element instanceof PsiReferenceExpression) {
        return null;
      }
      List<PsiNameIdentifierOwner> candidates = collectParentClassesAndMethodsUntilStatic(element, findFirstOnly);
      if (candidates.isEmpty()) return null;
      String name = element.getReferenceName();
      if (name == null) return null;
      return new Context(candidates, name);
    }
  }


  static List<PsiNameIdentifierOwner> collectParentClassesAndMethodsUntilStatic(PsiElement element, boolean findFirstOnly) {
    element = element.getParent();
    List<PsiNameIdentifierOwner> parents = new SmartList<>();
    while (element != null) {
      if (element instanceof PsiField && ((PsiField)element).hasModifierProperty(PsiModifier.STATIC)) {
        break;
      }
      if (element instanceof PsiMethod || element instanceof PsiClass) {
        if (((PsiMember)element).getName() != null) {
          parents.add((PsiNameIdentifierOwner)element);
          if (findFirstOnly) {
            return parents;
          }
        }
        if (((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC)) break;
      }
      element = element.getParent();
    }
    return parents;
  }
}
