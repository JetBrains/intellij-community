// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.JavaGenerateMemberCompletionContributor;
import com.intellij.codeInsight.completion.JavaKeywordCompletion;
import com.intellij.codeInsight.completion.JavaMemberNameCompletionContributor;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.codeInsight.generation.GetterSetterPrototypeProvider;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInsight.generation.TemplateGenerationInfo;
import com.intellij.icons.AllIcons;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.CommonCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.SyntheticElement;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.IconManager;
import com.intellij.util.SmartList;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.jetbrains.java.generate.exception.GenerateCodeException;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static com.intellij.patterns.PlatformPatterns.psiElement;

@NotNullByDefault
final class GenerateMemberItemProvider extends JavaModCompletionItemProvider {
  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    PsiElement position = context.getPosition();
    if (JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN.accepts(position)) return;
    if (psiElement(PsiIdentifier.class).withParents(PsiJavaCodeReferenceElement.class, PsiTypeElement.class, PsiClass.class).
      andNot(JavaKeywordCompletion.AFTER_DOT).accepts(position)) {
      PsiElement prevLeaf = PsiTreeUtil.prevVisibleLeaf(position);
      PsiModifierList modifierList = PsiTreeUtil.getParentOfType(prevLeaf, PsiModifierList.class);
      suggestGeneratedMethods(sink, position, modifierList);
    }
    else if (JavaGenerateMemberCompletionContributor.isTypingAnnotationForNewMember(position)) {
      PsiAnnotation annotation = Objects.requireNonNull(PsiTreeUtil.getParentOfType(position, PsiAnnotation.class));

      //sink.accept(itemWithOverrideImplementDialog());

      suggestGeneratedMethods(
        sink,
        position,
        (PsiModifierList)annotation.getParent());
    }
  }

  private static void suggestGeneratedMethods(ModCompletionResult sink,
                                              PsiElement position,
                                              @Nullable PsiModifierList modifierList) {
    PsiClass parent = CompletionUtil.getOriginalElement(Objects.requireNonNull(PsiTreeUtil.getParentOfType(position, PsiClass.class)));
    if (parent != null) {
      Set<MethodSignature> addedSignatures = new HashSet<>();
      addGetterSetterElements(sink, parent, addedSignatures);
      boolean generateDefaultMethods = modifierList != null && modifierList.hasModifierProperty(PsiModifier.DEFAULT);
      addSuperSignatureElements(parent, true, sink, addedSignatures, generateDefaultMethods);
      addSuperSignatureElements(parent, false, sink, addedSignatures, generateDefaultMethods);
    }
  }

  private static void addGetterSetterElements(ModCompletionResult sink,
                                              PsiClass parent,
                                              Set<? super MethodSignature> addedSignatures) {
    int count = 0;
    for (PsiField field : parent.getFields()) {
      if (isConstant(field)) continue;

      List<PsiMethod> prototypes = new SmartList<>();
      try {
        Collections.addAll(prototypes, GetterSetterPrototypeProvider.generateGetterSetters(field, true, false));
        if (!field.hasModifierProperty(PsiModifier.FINAL)) {
          Collections.addAll(prototypes, GetterSetterPrototypeProvider.generateGetterSetters(field, false, false));
        }
      }
      catch (GenerateCodeException ignore) {
      }
      for (final PsiMethod prototype : prototypes) {
        PsiMethod existingMethod = parent.findMethodBySignature(prototype, false);
        if ((existingMethod == null || existingMethod instanceof SyntheticElement) &&
            addedSignatures.add(prototype.getSignature(PsiSubstitutor.EMPTY))) {
          sink.accept(createGenerateMethodElement(prototype, PsiSubstitutor.EMPTY, 
                                                  () -> prototype.getIcon(Iconable.ICON_FLAG_VISIBILITY), 
                                                  "",
                                                  (completionStart, updater) -> {
                                                    removeLookupString(completionStart, updater);

                                                    insertGenerationInfos(completionStart, updater,
                                                                          Collections.singletonList(new PsiGenerationInfo<>(prototype)));
                                                  }, false, parent));

          if (count++ > 100) return;
        }
      }
    }
  }

  private static boolean isConstant(PsiField field) {
    return field.hasModifierProperty(PsiModifier.FINAL) &&
           field.hasModifierProperty(PsiModifier.PUBLIC) &&
           field.hasModifierProperty(PsiModifier.STATIC);
  }

  private static void removeLookupString(int completionStart, ModPsiUpdater updater) {
    Document document = updater.getDocument();
    document.deleteString(completionStart, updater.getCaretOffset());
    PsiDocumentManager.getInstance(updater.getProject()).commitDocument(document);
  }

  private static void addSuperSignatureElements(PsiClass parent,
                                                boolean implemented,
                                                ModCompletionResult sink,
                                                Set<? super MethodSignature> addedSignatures,
                                                boolean generateDefaultMethods) {
    for (CandidateInfo candidate : OverrideImplementExploreUtil.getMethodsToOverrideImplement(parent, implemented)) {
      PsiMethod baseMethod = (PsiMethod)candidate.getElement();
      PsiClass baseClass = baseMethod.getContainingClass();
      PsiSubstitutor substitutor = candidate.getSubstitutor();
      if (!baseMethod.isConstructor() && baseClass != null && addedSignatures.add(baseMethod.getSignature(substitutor))) {
        sink.accept(
          createOverridingLookupElement(implemented, baseMethod, baseClass, substitutor, generateDefaultMethods, parent));
      }
    }
  }

  private static CommonCompletionItem createOverridingLookupElement(boolean implemented,
                                                                    PsiMethod baseMethod,
                                                                    PsiClass baseClass,
                                                                    PsiSubstitutor substitutor,
                                                                    boolean generateDefaultMethods,
                                                                    PsiClass targetClass) {

    CommonCompletionItem.UpdateHandler handler = (int completionStart, ModPsiUpdater updater) -> {
      removeLookupString(completionStart, updater);

      PsiClass parent = PsiTreeUtil.findElementOfClassAtOffset(updater.getPsiFile(), completionStart, PsiClass.class, false);
      if (parent == null) return;

      List<PsiMethod> prototypes = OverrideImplementUtil.overrideOrImplementMethod(parent, baseMethod, false);
      insertGenerationInfos(completionStart, updater, OverrideImplementUtil.convert2GenerationInfos(prototypes));
    };
    Supplier<Icon> iconSupplier = () -> IconManager
      .getInstance()
      .createRowIcon(baseMethod.getIcon(0), implemented ? AllIcons.Gutter.ImplementingMethod : AllIcons.Gutter.OverridingMethod);
    return createGenerateMethodElement(baseMethod, substitutor, iconSupplier, baseClass.getName(), handler,
                                       generateDefaultMethods, targetClass);
  }

  private static void insertGenerationInfos(int completionStart,
                                            ModPsiUpdater updater,
                                            List<PsiGenerationInfo<PsiMethod>> infos) {
    List<PsiGenerationInfo<PsiMethod>> newInfos = GenerateMembersUtil
      .insertMembersAtOffset(updater.getPsiFile(), completionStart, infos);
    if (!newInfos.isEmpty()) {
      final List<PsiElement> elements = new ArrayList<>();
      for (GenerationInfo member : newInfos) {
        if (!(member instanceof TemplateGenerationInfo)) {
          ContainerUtil.addIfNotNull(elements, member.getPsiMember());
        }
      }

      newInfos.getFirst().positionCaret(updater, true);
    }
  }

  private static CommonCompletionItem createGenerateMethodElement(PsiMethod prototype,
                                                                  PsiSubstitutor substitutor,
                                                                  @UnknownNullability Supplier<Icon> icon,
                                                                  @Nullable @NlsSafe String typeText,
                                                                  CommonCompletionItem.UpdateHandler insertHandler,
                                                                  boolean generateDefaultMethod,
                                                                  PsiClass targetClass) {
    String methodName = prototype.getName();

    String visibility = VisibilityUtil.getVisibilityModifier(prototype.getModifierList());
    String modifiers = (visibility.equals(PsiModifier.PACKAGE_LOCAL) || visibility.equals(PsiModifier.PUBLIC) && targetClass.isInterface()
                        ? ""
                        : visibility + " ");
    if (generateDefaultMethod) {
      modifiers = "default " + modifiers;
    }

    PsiType type = substitutor.substitute(prototype.getReturnType());
    String signature = modifiers + (type == null ? "" : type.getPresentableText() + " ") + methodName;

    String parameters = "(" + StringUtil.join(prototype.getParameterList().getParameters(),
                                              p -> getShortParameterName(substitutor, p) + " " + p.getName(),
                                              ", ") + ")";

    String overrideSignature = " @Override " + signature; // leading space to make it a middle match, under all annotation suggestions
    String tailText = " {...}";
    MarkupText text = MarkupText.plainText(signature + parameters)
      .highlightAll(prototype.isDeprecated() ? MarkupText.Kind.STRIKEOUT : MarkupText.Kind.NORMAL)
      .concat(tailText, MarkupText.Kind.GRAYED);
    ModCompletionItemPresentation presentation = new ModCompletionItemPresentation(text)
      .withMainIcon(icon)
      .withDetailText(MarkupText.plainText(typeText == null ? "" : typeText));
    return new CommonCompletionItem(signature)
      .withObject(prototype)
      .addLookupString(methodName)
      .addLookupString(signature)
      .addLookupString(overrideSignature)
      .withPresentation(presentation)
      .withPriority(-1)
      .withAdditionalUpdater(insertHandler);
  }

  private static String getShortParameterName(PsiSubstitutor substitutor, PsiParameter p) {
    return PsiNameHelper.getShortClassName(substitutor.substitute(p.getType()).getPresentableText(false));
  }
}
