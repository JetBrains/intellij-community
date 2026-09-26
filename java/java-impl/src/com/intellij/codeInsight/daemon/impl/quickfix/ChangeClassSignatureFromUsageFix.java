// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCodeFragment;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeClassSignature.ChangeClassSignatureDialog;
import com.intellij.refactoring.changeClassSignature.ChangeClassSignatureDialog.TypeParameterInfoView;
import com.intellij.refactoring.changeClassSignature.Existing;
import com.intellij.refactoring.changeClassSignature.New;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChangeClassSignatureFromUsageFix extends BaseIntentionAction {
  private final PsiClass myClass;
  private final PsiReferenceParameterList myParameterList;

  public ChangeClassSignatureFromUsageFix(@NotNull PsiClass aClass, @NotNull PsiReferenceParameterList parameterList) {
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
    List<TypeParameterInfoView> parameters = createTypeParameters(myClass, Arrays.asList(parameterList.getTypeParameterElements()));
    String className = "class " + myClass.getName();
    String originClassDeclaration =
      className + (classTypeParameters.length == 0 ? "" : "<" + StringUtil.join(classTypeParameters, p -> p.getName(), ",") + ">");
    String modifiedClassDeclaration = className + "<" + StringUtil.join(parameters, p -> p.info().getName(classTypeParameters), ", ") + ">";
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, originClassDeclaration, modifiedClassDeclaration);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    List<TypeParameterInfoView> typeParameters = createTypeParameters(myClass, Arrays.asList(myParameterList.getTypeParameterElements()));
    ChangeClassSignatureDialog dialog = new ChangeClassSignatureDialog(myClass, typeParameters, false);
    dialog.show();
  }

  private static @NotNull List<TypeParameterInfoView> createTypeParameters(@NotNull PsiClass aClass,
                                                                           @NotNull List<? extends PsiTypeElement> typeElements) {
    PsiTypeParameter[] classTypeParameters = aClass.getTypeParameters();
    final TypeParameterNameSuggester suggester = new TypeParameterNameSuggester(classTypeParameters);

    JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(aClass.getProject());
    List<TypeParameterInfoView> result = new ArrayList<>();
    int listIndex = 0;
    for (PsiTypeElement typeElement : typeElements) {
      if (listIndex < classTypeParameters.length) {
        final PsiTypeParameter typeParameter = classTypeParameters[listIndex];

        if (isAssignable(typeParameter, typeElement.getType())) {
          PsiClassType[] types = typeParameter.getExtendsList().getReferencedTypes();
          if (types.length > 0) {
            PsiType type = types.length > 1 ? PsiIntersectionType.createIntersection(types) : types[0];
            PsiTypeCodeFragment fragment = ChangeClassSignatureDialog.createTableCodeFragment(type, aClass, factory, true);
            result.add(new TypeParameterInfoView(new Existing(listIndex++), fragment, null));
          }
          else {
            result.add(new TypeParameterInfoView(new Existing(listIndex++), null, null));
          }
          continue;
        }
      }

      final PsiType defaultType = typeElement.getType();
      final String suggestedName;
      PsiClassType boundType = null;
      if (defaultType instanceof PsiClassType type) {
        suggestedName = suggester.suggest(type);
        final PsiClass resolved = type.resolve();
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
      final PsiTypeCodeFragment boundFragment = ChangeClassSignatureDialog.createTableCodeFragment(boundType, typeElement, factory, true);
      PsiTypeCodeFragment defaultValueFragment = factory.createTypeCodeFragment(typeElement.getText(), typeElement, true);
      result.add(new TypeParameterInfoView(new New(suggestedName, defaultType, null), boundFragment, defaultValueFragment));
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
}
