// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeClassSignature.ChangeClassSignatureDialog;
import com.intellij.refactoring.changeClassSignature.Existing;
import com.intellij.refactoring.changeClassSignature.New;
import com.intellij.refactoring.changeClassSignature.TypeParameterInfo;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ChangeClassSignatureFromUsageFix extends BaseIntentionAction {
  private final PsiClass myClass;
  private final PsiReferenceParameterList myParameterList;

  public ChangeClassSignatureFromUsageFix(@NotNull PsiClass aClass,
                                          @NotNull PsiReferenceParameterList parameterList) {
    myClass = aClass;
    myParameterList = parameterList;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("change.class.signature.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (!myClass.isValid() || !myParameterList.isValid() || myClass instanceof PsiCompiledElement) {
      return false;
    }

    if (myClass.getTypeParameters().length >= myParameterList.getTypeArguments().length) {
      return false;
    }

    final PsiTypeParameterList classTypeParameterList = myClass.getTypeParameterList();
    if (classTypeParameterList == null) {
      return false;
    }

    setText(QuickFixBundle.message("change.class.signature.text", myClass.getName(), myParameterList.getText()));

    return true;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiReferenceParameterList parameterList = PsiTreeUtil.findSameElementInCopy(myParameterList, psiFile);
    PsiTypeParameter[] classTypeParameters = myClass.getTypeParameters();
    List<TypeParameterInfoView> parameters = createTypeParameters(JavaCodeFragmentFactory.getInstance(project),
                                                                  Arrays.asList(classTypeParameters),
                                                                  Arrays.asList(parameterList.getTypeParameterElements()));
    String className = "class " + myClass.getName();
    String originClassDeclaration = className + (classTypeParameters.length == 0 ? "" : "<" + StringUtil.join(classTypeParameters, p -> p.getName(), ",") + ">");
    String modifiedClassDeclaration = className + "<" + StringUtil.join(parameters, p -> p.getInfo().getName(classTypeParameters), ", ") + ">";
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, originClassDeclaration, modifiedClassDeclaration);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    final PsiTypeParameterList classTypeParameterList = myClass.getTypeParameterList();
    if (classTypeParameterList == null) {
      return;
    }

    ChangeClassSignatureDialog dialog = new ChangeClassSignatureDialog(
      myClass,
      createTypeParameters(
        JavaCodeFragmentFactory.getInstance(project),
        Arrays.asList(classTypeParameterList.getTypeParameters()),
        Arrays.asList(myParameterList.getTypeParameterElements())
      ),
      false
    );
    dialog.show();
  }

  private static @NotNull List<TypeParameterInfoView> createTypeParameters(@NotNull JavaCodeFragmentFactory factory,
                                                                           @NotNull List<? extends PsiTypeParameter> classTypeParameters,
                                                                           @NotNull List<? extends PsiTypeElement> typeElements) {
    final TypeParameterNameSuggester suggester = new TypeParameterNameSuggester(classTypeParameters);

    List<TypeParameterInfoView> result = new ArrayList<>();
    int listIndex = 0;
    for (PsiTypeElement typeElement : typeElements) {
      if (listIndex < classTypeParameters.size()) {
        final PsiTypeParameter typeParameter = classTypeParameters.get(listIndex);

        if (isAssignable(typeParameter, typeElement.getType())) {
          result.add(new TypeParameterInfoView(new Existing(listIndex++), null, null));
          continue;
        }
      }

      final PsiType defaultType = typeElement.getType();
      final String suggestedName;
      PsiClassType boundType = null;
      if (defaultType instanceof PsiClassType) {
        suggestedName = suggester.suggest((PsiClassType)defaultType);
        final PsiClass resolved = ((PsiClassType)defaultType).resolve();
        if (resolved != null) {
          final PsiReferenceList extendsList = resolved.getExtendsList();
          if (extendsList != null) {
            final PsiClassType[] types = extendsList.getReferencedTypes();
            if (types.length == 1) {
              boundType = types[0];
            }
          }
        }
      }
      else {
        suggestedName = suggester.suggestUnusedName("T");
      }
      final PsiTypeCodeFragment boundFragment = CommonJavaRefactoringUtil.createTableCodeFragment(boundType, typeElement, factory, true);
      result.add(new TypeParameterInfoView(new New(suggestedName, defaultType, null),
                                           boundFragment,
                                           boundType == null ? factory.createTypeCodeFragment(suggestedName, typeElement, true)
                                                             : CommonJavaRefactoringUtil
                                             .createTableCodeFragment(boundType, typeElement, factory, false)));
    }
    return result;
  }

  private static boolean isAssignable(@NotNull PsiTypeParameter typeParameter, @NotNull PsiType type) {
    for (PsiClassType t : typeParameter.getExtendsListTypes()) {
      if (!t.isAssignableFrom(type)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }


  private static class TypeParameterNameSuggester {
    private final Set<String> usedNames = new HashSet<>();

    TypeParameterNameSuggester(PsiTypeParameter @NotNull ... typeParameters) {
      this(Arrays.asList(typeParameters));
    }

    TypeParameterNameSuggester(@NotNull Collection<? extends PsiTypeParameter> typeParameters) {
      for (PsiTypeParameter p : typeParameters) {
        usedNames.add(p.getName());
      }
    }

    private @NotNull String suggestUnusedName(@NotNull String name) {
      String unusedName = name;
      int i = 0;
      while (true) {
        if (usedNames.add(unusedName)) {
          return unusedName;
        }
        unusedName = name + ++i;
      }
    }

    public @NotNull String suggest(@NotNull PsiClassType type) {
      return suggestUnusedName(StringUtil.toUpperCase(type.getClassName().substring(0, 1)));
    }
  }

  public static class TypeParameterInfoView {
    private final TypeParameterInfo myInfo;
    private final PsiTypeCodeFragment myBoundValueFragment;
    private final PsiTypeCodeFragment myDefaultValueFragment;

    public TypeParameterInfoView(TypeParameterInfo info, PsiTypeCodeFragment boundValueFragment, PsiTypeCodeFragment defaultValueFragment) {
      myInfo = info;
      myBoundValueFragment = boundValueFragment;
      myDefaultValueFragment = defaultValueFragment;
    }

    public TypeParameterInfo getInfo() {
      return myInfo;
    }

    public PsiTypeCodeFragment getBoundValueFragment() {
      return myBoundValueFragment;
    }

    public PsiTypeCodeFragment getDefaultValueFragment() {
      return myDefaultValueFragment;
    }
  }
}
