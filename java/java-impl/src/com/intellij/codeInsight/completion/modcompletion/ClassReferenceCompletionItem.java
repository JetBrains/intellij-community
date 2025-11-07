// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.modcompletion;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.completion.AllClassesGetter;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.CompletionItemPresentation;
import com.intellij.modcompletion.PsiUpdateCompletionItem;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.siyeh.ig.psiutils.JavaDeprecationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

@NotNullByDefault
public final class ClassReferenceCompletionItem extends PsiUpdateCompletionItem {
  private final PsiClass myClass;
  private final @Nullable String myQualifiedName;
  private final @Nullable String myForcedPresentableName;
  @NlsSafe private final String myPackageDisplayName;
  private final @Nullable Icon myIcon;
  private final boolean myStrikeout;
  private final PsiSubstitutor mySubstitutor;
  
  private ClassReferenceCompletionItem(PsiClass psiClass, PsiSubstitutor substitutor, @Nullable String forcedPresentableName) {
    myQualifiedName = psiClass.getQualifiedName();
    myClass = psiClass;
    myForcedPresentableName = forcedPresentableName;
    myPackageDisplayName = PsiFormatUtil.getPackageDisplayName(psiClass);
    myIcon = myClass.getIcon(Registry.is("ide.completion.show.visibility.icon") ? Iconable.ICON_FLAG_VISIBILITY : 0);
    myStrikeout = JavaDeprecationUtils.isDeprecated(psiClass, null);
    mySubstitutor = substitutor;
  }
  
  public ClassReferenceCompletionItem(PsiClass psiClass) {
    this(psiClass, PsiSubstitutor.EMPTY, null);
  }
  
  public ClassReferenceCompletionItem withPresentableName(@Nullable String forcedPresentableName) {
    return new ClassReferenceCompletionItem(myClass, mySubstitutor, forcedPresentableName);
  }
  
  public ClassReferenceCompletionItem withSubstitutor(PsiSubstitutor substitutor) {
    return new ClassReferenceCompletionItem(myClass, substitutor, myForcedPresentableName);
  }

  @Override
  public void update(ActionContext actionContext, InsertionContext insertionContext, PsiFile file, ModPsiUpdater updater) {
    AllClassesGetter.tryShorten(file, updater, myClass);
  }

  @Override
  public @NlsSafe String mainLookupString() {
    if (myForcedPresentableName != null) {
      return myForcedPresentableName;
    }
    return Objects.requireNonNull(myClass.getName());
  }

  @Override
  public PsiClass contextObject() {
    return myClass;
  }

  @Override
  public CompletionItemPresentation presentation() {
    String name = getName();
    String tailText = " " + myPackageDisplayName;
    if (mySubstitutor == PsiSubstitutor.EMPTY && myClass.getTypeParameters().length > 0) {
      String separator = "," + (showSpaceAfterComma(myClass) ? " " : "");
      tailText = "<" + StringUtil.join(myClass.getTypeParameters(), PsiTypeParameter::getName, separator) + ">" + tailText;
    }
    MarkupText mainText = MarkupText.builder()
      .append(name, myStrikeout ? MarkupText.Kind.STRIKEOUT : MarkupText.Kind.NORMAL)
      .append(tailText, MarkupText.Kind.GRAYED).build();
    return new CompletionItemPresentation(mainText)
      .withMainIcon(myIcon);
  }

  @NlsSafe
  private String getName() {
    if (myForcedPresentableName != null) {
      return myForcedPresentableName;
    }

    String name = PsiUtilCore.getName(myClass);

    if (mySubstitutor != PsiSubstitutor.EMPTY) {
      final PsiTypeParameter[] params = myClass.getTypeParameters();
      if (params.length > 0) {
        return name + formatTypeParameters(mySubstitutor, params);
      }
    }

    return StringUtil.notNullize(name);
  }

  private static String formatTypeParameters(final PsiSubstitutor substitutor, final PsiTypeParameter[] params) {
    final boolean space = showSpaceAfterComma(params[0]);
    StringBuilder buffer = new StringBuilder();
    buffer.append("<");
    for(int i = 0; i < params.length; i++){
      final PsiTypeParameter param = params[i];
      final PsiType type = substitutor.substitute(param);
      if(type == null){
        return "";
      }
      if (type instanceof PsiClassType classType && classType.getParameters().length > 0) {
        buffer.append(classType.rawType().getPresentableText()).append("<...>");
      } else {
        buffer.append(type.getPresentableText());
      }

      if(i < params.length - 1) {
        buffer.append(",");
        if (space) {
          buffer.append(" ");
        }
      }
    }
    buffer.append(">");
    return buffer.toString();
  }

  private static boolean showSpaceAfterComma(PsiClass element) {
    return CodeStyle.getLanguageSettings(element.getContainingFile(), JavaLanguage.INSTANCE).SPACE_AFTER_COMMA;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof ClassReferenceCompletionItem that)) return false;
    if (myQualifiedName != null) {
      return myQualifiedName.equals(that.myQualifiedName);
    }
    return Comparing.equal(myClass, that.myClass);
  }

  public @Nullable String getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public int hashCode() {
    final String s = myQualifiedName;
    return s == null ? 239 : s.hashCode();
  }

}
