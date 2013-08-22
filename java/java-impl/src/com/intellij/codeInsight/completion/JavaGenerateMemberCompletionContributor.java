/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.ui.RowIcon;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.util.Arrays;
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
      andNot(JavaCompletionData.AFTER_DOT).
      andNot(psiElement().afterLeaf(psiElement().inside(PsiModifierList.class))).accepts(position)) {
      final PsiClass parent = CompletionUtil.getOriginalElement((PsiClass)position.getParent().getParent().getParent());
      if (parent != null) {
        Set<MethodSignature> addedSignatures = ContainerUtil.newHashSet();
        addGetterSetterElements(result, parent, addedSignatures);
        addSuperSignatureElements(parent, true, result, addedSignatures);
        addSuperSignatureElements(parent, false, result, addedSignatures);
      }
    }

  }

  private static void addGetterSetterElements(CompletionResultSet result, PsiClass parent, Set<MethodSignature> addedSignatures) {
    List<PsiMethod> prototypes = ContainerUtil.newArrayList();
    for (PsiField field : parent.getFields()) {
      if (!(field instanceof PsiEnumConstant)) {
        prototypes.add(GenerateMembersUtil.generateGetterPrototype(field));
        prototypes.add(GenerateMembersUtil.generateSetterPrototype(field));
      }
    }
    for (final PsiMethod prototype : prototypes) {
      if (parent.findMethodBySignature(prototype, false) == null && addedSignatures.add(prototype.getSignature(PsiSubstitutor.EMPTY))) {
        Icon icon = prototype.getIcon(Iconable.ICON_FLAG_VISIBILITY);
        result.addElement(createGenerateMethodElement(prototype, PsiSubstitutor.EMPTY, icon, "", new InsertHandler<LookupElement>() {
          @Override
          public void handleInsert(InsertionContext context, LookupElement item) {
            removeLookupString(context);

            insertGenerationInfos(context, Arrays.asList(new PsiGenerationInfo<PsiMethod>(prototype)));
          }
        }));
      }
    }
  }

  private static void removeLookupString(InsertionContext context) {
    context.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());
    context.commitDocument();
  }

  private static void addSuperSignatureElements(final PsiClass parent, boolean implemented, CompletionResultSet result, Set<MethodSignature> addedSignatures) {
    for (CandidateInfo candidate : OverrideImplementExploreUtil.getMethodsToOverrideImplement(parent, implemented)) {
      PsiMethod baseMethod = (PsiMethod)candidate.getElement();
      assert baseMethod != null;
      PsiClass baseClass = baseMethod.getContainingClass();
      PsiSubstitutor substitutor = candidate.getSubstitutor();
      if (!baseMethod.isConstructor() && baseClass != null && addedSignatures.add(baseMethod.getSignature(substitutor))) {
        result.addElement(createOverridingLookupElement(parent, implemented, baseMethod, baseClass, substitutor));
      }
    }
  }

  private static LookupElementBuilder createOverridingLookupElement(final PsiClass parent,
                                                                    boolean implemented,
                                                                    final PsiMethod baseMethod,
                                                                    PsiClass baseClass, PsiSubstitutor substitutor) {

    RowIcon icon = new RowIcon(2);
    icon.setIcon(baseMethod.getIcon(0), 0);
    icon.setIcon(implemented ? AllIcons.Gutter.ImplementingMethod : AllIcons.Gutter.OverridingMethod, 1);

    return createGenerateMethodElement(baseMethod, substitutor, icon, baseClass.getName(), new InsertHandler<LookupElement>() {
      @Override
      public void handleInsert(InsertionContext context, LookupElement item) {
        removeLookupString(context);

        List<PsiMethod> prototypes = OverrideImplementUtil.overrideOrImplementMethod(parent, baseMethod, false);
        insertGenerationInfos(context, OverrideImplementUtil.convert2GenerationInfos(prototypes));
      }
    });
  }

  private static void insertGenerationInfos(InsertionContext context, List<PsiGenerationInfo<PsiMethod>> infos) {
    List<PsiGenerationInfo<PsiMethod>> newInfos = GenerateMembersUtil
      .insertMembersAtOffset(context.getFile(), context.getStartOffset(), infos);
    if (!newInfos.isEmpty()) {
      newInfos.get(0).positionCaret(context.getEditor(), true);
    }
  }

  private static LookupElementBuilder createGenerateMethodElement(PsiMethod prototype,
                                                                  PsiSubstitutor substitutor,
                                                                  Icon icon,
                                                                  String typeText, InsertHandler<LookupElement> insertHandler) {
    String methodName = prototype.getName();

    String visibility = VisibilityUtil.getVisibilityModifier(prototype.getModifierList());
    String modifiers = (visibility == PsiModifier.PACKAGE_LOCAL ? "" : visibility + " ");

    PsiType type = substitutor.substitute(prototype.getReturnType());
    String signature = modifiers + (type == null ? "" : type.getPresentableText() + " ") + methodName;

    String parameters = PsiFormatUtil.formatMethod(prototype, substitutor, PsiFormatUtilBase.SHOW_PARAMETERS, PsiFormatUtilBase.SHOW_NAME);

    LookupElementBuilder element = LookupElementBuilder.create(prototype, signature).withLookupString(methodName).
      withLookupString(signature).withInsertHandler(insertHandler).
      appendTailText(parameters, false).appendTailText(" {...}", true).withTypeText(typeText).withIcon(icon);
    element.putUserData(GENERATE_ELEMENT, true);
    return element;
  }
}
