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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.generation.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.RowIcon;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.exception.GenerateCodeException;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author peter
 */
public class JavaGenerateMemberCompletionContributor {
  static final Key<Boolean> GENERATE_ELEMENT = Key.create("GENERATE_ELEMENT");

  public static void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.BASIC && parameters.getCompletionType() != CompletionType.SMART) {
      return;
    }

    PsiElement position = parameters.getPosition();
    if (psiElement(PsiIdentifier.class).withParents(PsiJavaCodeReferenceElement.class, PsiTypeElement.class, PsiClass.class).
      andNot(JavaKeywordCompletion.AFTER_DOT).accepts(position)) {
      PsiElement prevLeaf = PsiTreeUtil.prevVisibleLeaf(position);
      PsiModifierList modifierList = PsiTreeUtil.getParentOfType(prevLeaf, PsiModifierList.class);
      if (modifierList != null) {
        result = result.withPrefixMatcher(position.getContainingFile().getText().substring(modifierList.getTextRange().getStartOffset(), parameters.getOffset()));
      }
      suggestGeneratedMethods(result, position, modifierList);
    } else if (psiElement(PsiIdentifier.class)
      .withParents(PsiJavaCodeReferenceElement.class, PsiAnnotation.class, PsiModifierList.class, PsiClass.class).accepts(position)) {
      PsiAnnotation annotation = ObjectUtils.assertNotNull(PsiTreeUtil.getParentOfType(position, PsiAnnotation.class));
      int annoStart = annotation.getTextRange().getStartOffset();
      suggestGeneratedMethods(result.withPrefixMatcher(annotation.getText().substring(0, parameters.getOffset() - annoStart)), position, (PsiModifierList)annotation.getParent());
    }

  }

  private static void suggestGeneratedMethods(CompletionResultSet result, PsiElement position, @Nullable PsiModifierList modifierList) {
    PsiClass parent = CompletionUtil.getOriginalElement(ObjectUtils.assertNotNull(PsiTreeUtil.getParentOfType(position, PsiClass.class)));
    if (parent != null) {
      Set<MethodSignature> addedSignatures = ContainerUtil.newHashSet();
      addGetterSetterElements(result, parent, addedSignatures);
      boolean generateDefaultMethods = modifierList != null && modifierList.hasModifierProperty(PsiModifier.DEFAULT);
      addSuperSignatureElements(parent, true, result, addedSignatures, generateDefaultMethods);
      addSuperSignatureElements(parent, false, result, addedSignatures, generateDefaultMethods);
    }
  }

  private static void addGetterSetterElements(CompletionResultSet result, PsiClass parent, Set<MethodSignature> addedSignatures) {
    int count = 0;
    for (PsiField field : parent.getFields()) {
      if (isConstant(field)) continue;

      List<PsiMethod> prototypes = ContainerUtil.newSmartList();
      try {
        Collections.addAll(prototypes, GetterSetterPrototypeProvider.generateGetterSetters(field, true, false));
        if (!field.hasModifierProperty(PsiModifier.FINAL)) {
          Collections.addAll(prototypes, GetterSetterPrototypeProvider.generateGetterSetters(field, false, false));
        }
      }
      catch (GenerateCodeException ignore) { }
      for (final PsiMethod prototype : prototypes) {
        if (parent.findMethodBySignature(prototype, false) == null && addedSignatures.add(prototype.getSignature(PsiSubstitutor.EMPTY))) {
          Icon icon = prototype.getIcon(Iconable.ICON_FLAG_VISIBILITY);
          result.addElement(createGenerateMethodElement(prototype, PsiSubstitutor.EMPTY, icon, "", new InsertHandler<LookupElement>() {
            @Override
            public void handleInsert(InsertionContext context, LookupElement item) {
              removeLookupString(context);

              insertGenerationInfos(context, Collections.singletonList(new PsiGenerationInfo<>(prototype)));
            }
          }, false, parent));
          
          if (count++ > 100) return;
        }
      }
    }
  }

  private static boolean isConstant(PsiField field) {
    return field.hasModifierProperty(PsiModifier.FINAL) && field.hasModifierProperty(PsiModifier.PUBLIC) && field.hasModifierProperty(PsiModifier.STATIC);
  }

  private static void removeLookupString(InsertionContext context) {
    context.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());
    context.commitDocument();
  }

  private static void addSuperSignatureElements(PsiClass parent, boolean implemented, CompletionResultSet result, Set<MethodSignature> addedSignatures, boolean generateDefaultMethods) {
    for (CandidateInfo candidate : OverrideImplementExploreUtil.getMethodsToOverrideImplement(parent, implemented)) {
      PsiMethod baseMethod = (PsiMethod)candidate.getElement();
      PsiClass baseClass = baseMethod.getContainingClass();
      PsiSubstitutor substitutor = candidate.getSubstitutor();
      if (!baseMethod.isConstructor() && baseClass != null && addedSignatures.add(baseMethod.getSignature(substitutor))) {
        result.addElement(createOverridingLookupElement(implemented, baseMethod, baseClass, substitutor, generateDefaultMethods, parent));
      }
    }
  }

  private static LookupElementBuilder createOverridingLookupElement(boolean implemented,
                                                                    final PsiMethod baseMethod,
                                                                    PsiClass baseClass, PsiSubstitutor substitutor, boolean generateDefaultMethods, PsiClass targetClass) {

    RowIcon icon = new RowIcon(baseMethod.getIcon(0), implemented ? AllIcons.Gutter.ImplementingMethod : AllIcons.Gutter.OverridingMethod);
    return createGenerateMethodElement(baseMethod, substitutor, icon, baseClass.getName(), new InsertHandler<LookupElement>() {
      @Override
      public void handleInsert(InsertionContext context, LookupElement item) {
        removeLookupString(context);

        final PsiClass parent = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiClass.class, false);
        if (parent == null) return;

        try (AccessToken ignored = generateDefaultMethods ? forceDefaultMethodsInside() : AccessToken.EMPTY_ACCESS_TOKEN) {
          List<PsiMethod> prototypes = OverrideImplementUtil.overrideOrImplementMethod(parent, baseMethod, false);
          insertGenerationInfos(context, OverrideImplementUtil.convert2GenerationInfos(prototypes));
        }
      }

    }, generateDefaultMethods, targetClass);
  }

  private static AccessToken forceDefaultMethodsInside() {
    CommandProcessor instance = CommandProcessor.getInstance();
    String commandName = instance.getCurrentCommandName();
    instance.setCurrentCommandName(OverrideImplementUtil.IMPLEMENT_COMMAND_MARKER);
    return new AccessToken() {
      @Override
      public void finish() {
        instance.setCurrentCommandName(commandName);
      }
    };
  }

  private static void insertGenerationInfos(InsertionContext context, List<PsiGenerationInfo<PsiMethod>> infos) {
    List<PsiGenerationInfo<PsiMethod>> newInfos = GenerateMembersUtil
      .insertMembersAtOffset(context.getFile(), context.getStartOffset(), infos);
    if (!newInfos.isEmpty()) {
      final List<PsiElement> elements = new ArrayList<>();
      for (GenerationInfo member : newInfos) {
        if (!(member instanceof TemplateGenerationInfo)) {
          elements.add(member.getPsiMember());
        }
      }

      GlobalInspectionContextBase.cleanupElements(context.getProject(), null, elements.toArray(new PsiElement[elements.size()]));
      newInfos.get(0).positionCaret(context.getEditor(), true);
    }
  }

  private static LookupElementBuilder createGenerateMethodElement(PsiMethod prototype,
                                                                  PsiSubstitutor substitutor,
                                                                  Icon icon,
                                                                  String typeText, InsertHandler<LookupElement> insertHandler,
                                                                  boolean generateDefaultMethod,
                                                                  PsiClass targetClass) {
    String methodName = prototype.getName();

    String visibility = VisibilityUtil.getVisibilityModifier(prototype.getModifierList());
    String modifiers = (visibility == PsiModifier.PACKAGE_LOCAL || visibility == PsiModifier.PUBLIC && targetClass.isInterface() ? "" : visibility + " ");
    if (generateDefaultMethod) {
      modifiers = "default " + modifiers;
    }

    PsiType type = substitutor.substitute(prototype.getReturnType());
    String typeAndName = (type == null ? "" : type.getPresentableText() + " ") + methodName;
    String signature = modifiers + typeAndName;

    String parameters = "(" + StringUtil.join(prototype.getParameterList().getParameters(),
                                              p -> getShortParameterName(substitutor, p) + " " + p.getName(),
                                              ", ") + ")";

    String overrideSignature = " @Override " + signature; // leading space to make it a middle match, under all annotation suggestions
    LookupElementBuilder element = LookupElementBuilder.create(prototype, signature).withLookupString(methodName).
      withLookupString(typeAndName).
      withLookupString(signature).withLookupString(overrideSignature).withInsertHandler(insertHandler).
      appendTailText(parameters, false).appendTailText(" {...}", true).withTypeText(typeText).withIcon(icon);
    if (prototype.isDeprecated()) {
      element = element.withStrikeoutness(true);
    }
    element.putUserData(GENERATE_ELEMENT, true);
    return element;
  }

  @NotNull
  private static String getShortParameterName(PsiSubstitutor substitutor, PsiParameter p) {
    return PsiNameHelper.getShortClassName(substitutor.substitute(p.getType()).getPresentableText(false));
  }
}
