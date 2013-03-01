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
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.ui.RowIcon;
import com.intellij.util.VisibilityUtil;

import java.util.List;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author peter
 */
public class JavaOverrideCompletionContributor {
  static final Key<Boolean> OVERRIDE_ELEMENT = Key.create("OVERRIDE_ELEMENT");

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
        addSuperSignatureElements(parent, true, result);
        addSuperSignatureElements(parent, false, result);
      }
    }

  }

  private static void addSuperSignatureElements(final PsiClass parent, boolean implemented, CompletionResultSet result) {
    for (CandidateInfo candidate : OverrideImplementExploreUtil.getMethodsToOverrideImplement(parent, implemented)) {
      PsiMethod baseMethod = (PsiMethod)candidate.getElement();
      PsiClass baseClass = baseMethod.getContainingClass();
      if (!baseMethod.isConstructor() && baseClass != null) {
        result.addElement(createOverridingLookupElement(parent, implemented, baseMethod, baseClass, candidate.getSubstitutor()));
      }
    }
  }

  private static LookupElementBuilder createOverridingLookupElement(final PsiClass parent,
                                                                    boolean implemented,
                                                                    final PsiMethod baseMethod,
                                                                    PsiClass baseClass, PsiSubstitutor substitutor) {
    String methodName = baseMethod.getName();

    String visibility = VisibilityUtil.getVisibilityModifier(baseMethod.getModifierList());
    String modifiers = (visibility == PsiModifier.PACKAGE_LOCAL ? "" : visibility + " ");

    PsiType type = substitutor.substitute(baseMethod.getReturnType());
    String signature = modifiers + (type == null ? "" : type.getPresentableText() + " ") + methodName;

    String parameters = PsiFormatUtil.formatMethod(baseMethod, substitutor, PsiFormatUtilBase.SHOW_PARAMETERS, PsiFormatUtilBase.SHOW_NAME);

    InsertHandler<LookupElement> insertHandler = new InsertHandler<LookupElement>() {
      @Override
      public void handleInsert(InsertionContext context, LookupElement item) {
        context.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());
        context.commitDocument();

        List<PsiMethod> prototypes = OverrideImplementUtil.overrideOrImplementMethod(parent, baseMethod, false);
        List<PsiGenerationInfo<PsiMethod>> infos = OverrideImplementUtil.convert2GenerationInfos(prototypes);
        List<PsiGenerationInfo<PsiMethod>> newInfos = GenerateMembersUtil.insertMembersAtOffset(context.getFile(), context.getStartOffset(), infos);
        if (!newInfos.isEmpty()) {
          newInfos.get(0).positionCaret(context.getEditor(), true);
        }
      }
    };

    RowIcon icon = new RowIcon(2);
    icon.setIcon(baseMethod.getIcon(0), 0);
    icon.setIcon(implemented ? AllIcons.Gutter.ImplementingMethod : AllIcons.Gutter.OverridingMethod, 1);

    LookupElementBuilder element = LookupElementBuilder.create(baseMethod, signature).withLookupString(methodName).
      withLookupString(signature).withInsertHandler(insertHandler).
      appendTailText(parameters, false).appendTailText(" {...}", true).withTypeText(baseClass.getName()).withIcon(icon);
    element.putUserData(OVERRIDE_ELEMENT, true);
    return element;
  }
}
