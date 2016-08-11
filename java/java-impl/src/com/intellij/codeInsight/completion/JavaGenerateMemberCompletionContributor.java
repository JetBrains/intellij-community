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
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.ui.RowIcon;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.util.*;

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
      andNot(JavaKeywordCompletion.AFTER_DOT).
      andNot(psiElement().afterLeaf(psiElement().inside(PsiModifierList.class))).accepts(position)) {
      suggestGeneratedMethods(result, position);
    } else if (psiElement(PsiIdentifier.class)
      .withParents(PsiJavaCodeReferenceElement.class, PsiAnnotation.class, PsiModifierList.class, PsiClass.class).accepts(position)) {
      PsiAnnotation annotation = ObjectUtils.assertNotNull(PsiTreeUtil.getParentOfType(position, PsiAnnotation.class));
      int annoStart = annotation.getTextRange().getStartOffset();
      suggestGeneratedMethods(result.withPrefixMatcher(annotation.getText().substring(0, parameters.getOffset() - annoStart)), position);
    }

  }

  private static void suggestGeneratedMethods(CompletionResultSet result, PsiElement position) {
    PsiClass parent = CompletionUtil.getOriginalElement(ObjectUtils.assertNotNull(PsiTreeUtil.getParentOfType(position, PsiClass.class)));
    if (parent != null) {
      Set<MethodSignature> addedSignatures = ContainerUtil.newHashSet();
      addGetterSetterElements(result, parent, addedSignatures);
      addSuperSignatureElements(parent, true, result, addedSignatures);
      addSuperSignatureElements(parent, false, result, addedSignatures);
    }
  }

  private static void addGetterSetterElements(CompletionResultSet result, PsiClass parent, Set<MethodSignature> addedSignatures) {
    int count = 0;
    for (PsiField field : parent.getFields()) {
      if (field instanceof PsiEnumConstant) continue;

      List<PsiMethod> prototypes = ContainerUtil.newSmartList();
      Collections.addAll(prototypes, GetterSetterPrototypeProvider.generateGetterSetters(field, true));
      Collections.addAll(prototypes, GetterSetterPrototypeProvider.generateGetterSetters(field, false));
      for (final PsiMethod prototype : prototypes) {
        if (parent.findMethodBySignature(prototype, false) == null && addedSignatures.add(prototype.getSignature(PsiSubstitutor.EMPTY))) {
          Icon icon = prototype.getIcon(Iconable.ICON_FLAG_VISIBILITY);
          result.addElement(createGenerateMethodElement(prototype, PsiSubstitutor.EMPTY, icon, "", new InsertHandler<LookupElement>() {
            @Override
            public void handleInsert(InsertionContext context, LookupElement item) {
              removeLookupString(context);

              insertGenerationInfos(context, Collections.singletonList(new PsiGenerationInfo<>(prototype)));
            }
          }));
          
          if (count++ > 100) return;
        }
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
      PsiClass baseClass = baseMethod.getContainingClass();
      PsiSubstitutor substitutor = candidate.getSubstitutor();
      if (!baseMethod.isConstructor() && baseClass != null && addedSignatures.add(baseMethod.getSignature(substitutor))) {
        result.addElement(createOverridingLookupElement(implemented, baseMethod, baseClass, substitutor));
      }
    }
  }

  private static LookupElementBuilder createOverridingLookupElement(boolean implemented,
                                                                    final PsiMethod baseMethod,
                                                                    PsiClass baseClass, PsiSubstitutor substitutor) {

    RowIcon icon = new RowIcon(baseMethod.getIcon(0), implemented ? AllIcons.Gutter.ImplementingMethod : AllIcons.Gutter.OverridingMethod);
    return createGenerateMethodElement(baseMethod, substitutor, icon, baseClass.getName(), new InsertHandler<LookupElement>() {
      @Override
      public void handleInsert(InsertionContext context, LookupElement item) {
        removeLookupString(context);

        final PsiClass parent = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiClass.class, false);
        if (parent == null) return;

        List<PsiMethod> prototypes = OverrideImplementUtil.overrideOrImplementMethod(parent, baseMethod, false);
        insertGenerationInfos(context, OverrideImplementUtil.convert2GenerationInfos(prototypes));
      }
    });
  }

  private static void insertGenerationInfos(InsertionContext context, List<PsiGenerationInfo<PsiMethod>> infos) {
    List<PsiGenerationInfo<PsiMethod>> newInfos = GenerateMembersUtil
      .insertMembersAtOffset(context.getFile(), context.getStartOffset(), infos);
    if (!newInfos.isEmpty()) {
      final List<PsiElement> elements = new ArrayList<>();
      for (GenerationInfo member : newInfos) {
        if (!(member instanceof TemplateGenerationInfo)) {
          final PsiMember psiMember = member.getPsiMember();
          if (psiMember != null) {
            elements.add(psiMember);
          }
        }
      }

      GlobalInspectionContextBase.cleanupElements(context.getProject(), null, elements.toArray(new PsiElement[elements.size()]));
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

    String overrideSignature = " @Override " + signature; // leading space to make it a middle match, under all annotation suggestions
    LookupElementBuilder element = LookupElementBuilder.create(prototype, signature).withLookupString(methodName).
      withLookupString(signature).withLookupString(overrideSignature).withInsertHandler(insertHandler).
      appendTailText(parameters, false).appendTailText(" {...}", true).withTypeText(typeText).withIcon(icon);
    if (prototype.isDeprecated()) {
      element = element.setStrikeout(true);
    }
    element.putUserData(GENERATE_ELEMENT, true);
    return element;
  }
}
