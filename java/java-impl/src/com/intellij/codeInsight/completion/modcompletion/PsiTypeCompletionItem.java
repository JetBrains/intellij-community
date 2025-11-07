// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.modcompletion;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.CompletionItemPresentation;
import com.intellij.modcompletion.PsiUpdateCompletionItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

@NotNullByDefault
public final class PsiTypeCompletionItem extends PsiUpdateCompletionItem {
  private final PsiType myType;
  private final boolean myIndicateAnonymous;
  private final boolean myAddArrayInitializer;
  private final String myLocationString;
  private final @Nullable Icon myIcon;
  private final boolean myStrikeout;
  private final @Nullable String myForcedPresentableName;
  @NlsSafe private final String myLookupString;

  private PsiTypeCompletionItem(PsiType type, boolean indicateAnonymous, boolean addArrayInitializer, String locationString,
                                @NonNls String lookupString) {
    myType = type;
    myIndicateAnonymous = indicateAnonymous;
    myAddArrayInitializer = addArrayInitializer;
    myLocationString = locationString;
    myLookupString = lookupString;
    myForcedPresentableName = null;
    PsiClass psiClass = PsiUtil.resolveClassInType(type);
    myIcon =
      psiClass == null ? null : psiClass.getIcon(Registry.is("ide.completion.show.visibility.icon") ? Iconable.ICON_FLAG_VISIBILITY : 0);
    myStrikeout = psiClass != null && psiClass.isDeprecated();
  }

  public PsiTypeCompletionItem(PsiType type, @NonNls String lookupString) {
    this(type, false, false, "", lookupString);
  }

  @Contract(pure = true)
  public PsiTypeCompletionItem withPresentableName(@Nullable String forcedPresentableName) {
    return new PsiTypeCompletionItem(myType, myIndicateAnonymous, myAddArrayInitializer, myLocationString, myLookupString);
  }

  @Contract(pure = true)
  public PsiTypeCompletionItem indicateAnonymous() {
    return new PsiTypeCompletionItem(myType, true, myAddArrayInitializer, myLocationString, myLookupString);
  }

  @Contract(pure = true)
  public PsiTypeCompletionItem addArrayInitializer() {
    return new PsiTypeCompletionItem(myType, myIndicateAnonymous, true, myLocationString, myLookupString);
  }

  @Contract(pure = true)
  public PsiTypeCompletionItem showPackage() {
    PsiClass psiClass = PsiUtil.resolveClassInType(myType);
    if (psiClass != null) {
      return new PsiTypeCompletionItem(myType, myIndicateAnonymous, myAddArrayInitializer,
                                       " (" + PsiFormatUtil.getPackageDisplayName(psiClass) + ")", myLookupString);
    }
    return this;
  }

  @Override
  public boolean isValid() {
    return myType.isValid();
  }

  public @Nullable String getForcedPresentableName() {
    return myForcedPresentableName;
  }

  @Override
  public String mainLookupString() {
    return myLookupString;
  }

  @Override
  public PsiType contextObject() {
    return myType;
  }

  @Override
  public void update(ActionContext actionContext, InsertionContext insertionContext, PsiFile file, ModPsiUpdater updater) {
    PsiClass psiClass = PsiUtil.resolveClassInType(myType);
    if (psiClass != null) {
      addImportForItem(psiClass, file, actionContext.selection().getStartOffset(), updater);
    }

    PsiElement position = file.findElementAt(actionContext.selection().getStartOffset());
    boolean insideTypeElement = false;
    Document document = updater.getDocument();
    if (position != null) {
      insideTypeElement = position.getParent() instanceof PsiTypeElement ||
                          position.getParent().getParent() instanceof PsiTypeElement;
      int genericsStart = updater.getCaretOffset();
      String generics = "";
      if (insertionContext.insertionCharacter() != '<') {
        generics = calcGenerics(position, psiClass);
      }
      document.insertString(genericsStart, generics);
      updater.moveCaretTo(genericsStart + generics.length());
      JavaCompletionUtil.shortenReference(file, genericsStart - 1);
    }

    int curOffset = updater.getCaretOffset();
    int targetOffset = curOffset;
    String braces = StringUtil.repeat("[]", myType.getArrayDimensions());
    if (!braces.isEmpty()) {
      if (myAddArrayInitializer) {
        document.insertString(targetOffset, braces + "{}");
        targetOffset += braces.length() + 1;
      }
      else {
        document.insertString(targetOffset, braces);
        targetOffset += insideTypeElement ? braces.length() : 1;
      }
      updater.registerTabOut(TextRange.create(curOffset, curOffset), targetOffset);
    }
    updater.moveCaretTo(targetOffset);
  }

  private @NlsSafe String calcGenerics(@Nullable PsiElement context, @Nullable PsiClass psiClass) {
    if (PsiTypeLookupItem.isDiamond(myType)) {
      return "<>";
    }

    if (psiClass != null) {
      PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(psiClass.getProject()).getResolveHelper();
      PsiSubstitutor substitutor = getSubstitutor();
      StringBuilder builder = new StringBuilder();
      for (PsiTypeParameter parameter : psiClass.getTypeParameters()) {
        PsiType substitute = substitutor.substitute(parameter);
        String name = parameter.getName();
        if (substitute == null ||
            (PsiUtil.resolveClassInType(substitute) == parameter &&
             name != null && context != null && resolveHelper.resolveReferencedClass(name, context) != CompletionUtil.getOriginalOrSelf(parameter))) {
          return "";
        }
        if (!builder.isEmpty()) {
          builder.append(", ");
        }
        builder.append(substitute.getCanonicalText());
      }
      if (!builder.isEmpty()) {
        return "<" + builder + ">";
      }
    }
    return "";
  }

  private PsiSubstitutor getSubstitutor() {
    if (myType.getDeepComponentType() instanceof PsiClassType classType) {
      return classType.resolveGenerics().getSubstitutor();
    }
    return PsiSubstitutor.EMPTY;
  }

  public static PsiTypeCompletionItem create(PsiType type) {
    PsiType typeToPresent = type.getDeepComponentType();
    if (typeToPresent instanceof PsiClassType classType) {
      typeToPresent = classType.rawType();
    }
    return new PsiTypeCompletionItem(type, typeToPresent.getPresentableText());
  }

  @Override
  public CompletionItemPresentation presentation() {
    MarkupText text = MarkupText.plainText(myLookupString);
    text = text.concat(calcGenerics(null, PsiUtil.resolveClassInType(myType)), MarkupText.Kind.NORMAL);
    text = text.concat("[]".repeat(myType.getArrayDimensions()), MarkupText.Kind.NORMAL);
    if (myType instanceof PsiPrimitiveType) {
      text = text.highlightAll(MarkupText.Kind.STRONG);
    }
    if (myStrikeout) {
      text = text.highlightAll(MarkupText.Kind.STRIKEOUT);
    }
    if (myAddArrayInitializer) {
      text = text.concat("{...}", MarkupText.Kind.GRAYED);
    }
    return new CompletionItemPresentation(text).withMainIcon(myIcon);
  }

  public static void addImportForItem(PsiClass aClass, PsiFile file, int startOffset, ModPsiUpdater updater) {
    if (aClass.getQualifiedName() == null) return;

    int tail = updater.getCaretOffset();

    PsiJavaCodeReferenceElement ref =
      PsiTreeUtil.findElementOfClassAtOffset(file, tail - 1, PsiJavaCodeReferenceElement.class, false);
    boolean goneDeeper = false;
    while (ref != null) {
      PsiElement qualifier = ref.getQualifier();
      PsiClass outer = aClass.getContainingClass();
      if (!Objects.equals(aClass.getName(), ref.getReferenceName())) {
        if (!JavaPsiFacade.getInstance(aClass.getProject()).getResolveHelper().isAccessible(aClass, ref, outer)) {
          // An inner class of non-public superclass is accessed via public subclass: do not rationalize qualifier in this case
          return;
        }
        break;
      }
      if (!(qualifier instanceof PsiJavaCodeReferenceElement) || outer == null) break;

      goneDeeper = true;
      ref = (PsiJavaCodeReferenceElement)qualifier;
      aClass = outer;
    }

    JavaCompletionUtil.insertClassReference(aClass, file,
                                            goneDeeper ? ref.getTextRange().getStartOffset() : startOffset,
                                            goneDeeper ? ref.getTextRange().getEndOffset() : tail);
    // jigsaw module
    if (PsiUtil.isAvailable(JavaFeature.MODULES, file)) {
      PsiJavaModule fromDescriptor = JavaModuleGraphUtil.findDescriptorByElement(updater.getOriginalFile(file));
      if (fromDescriptor != null) {
        PsiJavaModule toDescriptor = JavaModuleGraphUtil.findDescriptorByElement(aClass);
        if (toDescriptor != null) {
          if (!JavaModuleGraphHelper.getInstance().isAccessible(aClass, file)) return;
          JavaModuleGraphUtil.addDependency(updater.getWritable(fromDescriptor), toDescriptor, null);
        }
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof PsiTypeCompletionItem item)) return false;

    return myIndicateAnonymous == item.myIndicateAnonymous &&
           myAddArrayInitializer == item.myAddArrayInitializer &&
           myType.equals(item.myType);
  }

  @Override
  public int hashCode() {
    int result = myType.hashCode();
    result = 31 * result + Boolean.hashCode(myIndicateAnonymous);
    result = 31 * result + Boolean.hashCode(myAddArrayInitializer);
    return result;
  }
}
