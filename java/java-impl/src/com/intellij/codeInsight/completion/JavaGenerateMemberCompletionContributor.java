// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.generation.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.RowIcon;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.exception.GenerateCodeException;

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
      andNot(JavaKeywordCompletion.AFTER_DOT).accepts(position)) {
      int priority = -1;
      boolean followedByWhitespace = psiElement()
        .withSuperParent(
          2, // Go to PsiTypeElement
          psiElement().beforeSiblingSkipping(
            psiElement(PsiErrorElement.class), // If no annotations are present, the Psi will contain a "identifier expected" node right after
            psiElement(PsiWhiteSpace.class)
              //.withText(StandardPatterns.string().matches("(?s) *\n *\n.*")) // Require line to be followed by empty line
          )).accepts(position);
      if (followedByWhitespace) {
        priority = 50;
      }
      PsiElement prevLeaf = PsiTreeUtil.prevVisibleLeaf(position);
      PsiModifierList modifierList = PsiTreeUtil.getParentOfType(prevLeaf, PsiModifierList.class);

      boolean hasOverrideAnnotation = false;
      if (modifierList != null) {
        String fileText = position.getContainingFile().getText();
        String prefix = fileText.substring(modifierList.getTextRange().getStartOffset(), parameters.getOffset());
        PsiAnnotation[] annotations = PsiTreeUtil.getChildrenOfType(modifierList, PsiAnnotation.class);
        if (annotations != null) {
          for (PsiAnnotation annotation : annotations) {
            if (Override.class.getName().equals(annotation.getQualifiedName())) {
              hasOverrideAnnotation = true;
              break;
            }
          }
        }
        result = result.withPrefixMatcher(new NoMiddleMatchesAfterSpace(
          prefix));
      }
      suggestGeneratedMethods(result, position, modifierList, !hasOverrideAnnotation, priority, hasOverrideAnnotation, false);
    } else if (psiElement(PsiIdentifier.class)
      .withParents(PsiJavaCodeReferenceElement.class, PsiAnnotation.class, PsiModifierList.class).accepts(position)) {
      // @Override<CURSOR>
      // @Overr<CURSOR>
      PsiAnnotation annotation = ObjectUtils.assertNotNull(PsiTreeUtil.getParentOfType(position, PsiAnnotation.class));
      int annoStart = annotation.getTextRange().getStartOffset();
      suggestGeneratedMethods(
        result.withPrefixMatcher(new NoMiddleMatchesAfterSpace(annotation.getText().substring(0, parameters.getOffset() - annoStart))),
        position,
        (PsiModifierList)annotation.getParent(), false, 50, false, true);
    }
    else if (psiElement(PsiIdentifier.class).withParents(PsiField.class).accepts(position)) {
      PsiElement parent = position.getParent();
      if (parent.getLastChild() instanceof PsiErrorElement && parent instanceof PsiField) {
        // identifier identifier<caret> <no semicolon>
        // Missing semicolon after field, so maybe the user wants to create an overload
        PsiField field = (PsiField)parent;
        int fieldStart = field.getTextRange().getStartOffset();
        boolean hasOverrideAnnotation = field.hasAnnotation(Override.class.getName());
        suggestGeneratedMethods(
          result.withPrefixMatcher(new NoMiddleMatchesAfterSpace(field.getText().substring(0, parameters.getOffset() - fieldStart)))
          , position, field.getModifierList(), !hasOverrideAnnotation, 50, hasOverrideAnnotation, false);
      }
    }
  }

  private static void suggestGeneratedMethods(CompletionResultSet result,
                                              PsiElement position,
                                              @Nullable PsiModifierList modifierList,
                                              boolean includeGettersSetters, int priority, boolean forceOverrideAnnotation, boolean deprioritizeGenerated) {
    PsiClass parent = CompletionUtil.getOriginalElement(ObjectUtils.assertNotNull(PsiTreeUtil.getParentOfType(position, PsiClass.class)));
    if (parent != null) {
      Set<MethodSignature> addedSignatures = new HashSet<>();
      if (includeGettersSetters) {
        addGetterSetterElements(result, parent, addedSignatures, priority);
      }
      boolean generateDefaultMethods = modifierList != null && modifierList.hasModifierProperty(PsiModifier.DEFAULT);

      addSuperSignatureElements(parent, true, result, addedSignatures, generateDefaultMethods, priority, deprioritizeGenerated, forceOverrideAnnotation);
      addSuperSignatureElements(parent, false, result, addedSignatures, generateDefaultMethods, priority, deprioritizeGenerated, forceOverrideAnnotation);
    }
  }

  private static void addGetterSetterElements(CompletionResultSet result,
                                              PsiClass parent,
                                              Set<? super MethodSignature> addedSignatures, int priority) {
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
            public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
              removeLookupString(context);

              insertGenerationInfos(context, Collections.singletonList(new PsiGenerationInfo<>(prototype)));
            }
          }, false, parent, priority, true));

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

  private static void addSuperSignatureElements(PsiClass parent,
                                                boolean implemented,
                                                CompletionResultSet result,
                                                Set<? super MethodSignature> addedSignatures,
                                                boolean generateDefaultMethods,
                                                int priority,
                                                boolean deprioritize,
                                                boolean forceOverrideAnnotation) {
    for (CandidateInfo candidate : OverrideImplementExploreUtil.getMethodsToOverrideImplement(parent, implemented)) {
      PsiMethod baseMethod = (PsiMethod)candidate.getElement();
      PsiClass baseClass = baseMethod.getContainingClass();
      PsiSubstitutor substitutor = candidate.getSubstitutor();
      if (!baseMethod.isConstructor() && baseClass != null && addedSignatures.add(baseMethod.getSignature(substitutor))) {
        int priorityModifier = 0;
        // Sort abstract methods above implemented methods
        if (baseMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
          priorityModifier = + 2;
        }
        result.addElement(createOverridingLookupElement(implemented, baseMethod, baseClass, substitutor, generateDefaultMethods, parent,
                                                        priority + priorityModifier, deprioritize, forceOverrideAnnotation));
      }
    }
  }

  private static LookupElement createOverridingLookupElement(boolean implemented,
                                                             PsiMethod baseMethod,
                                                             PsiClass baseClass,
                                                             PsiSubstitutor substitutor,
                                                             boolean generateDefaultMethods,
                                                             PsiClass targetClass,
                                                             int priority,
                                                             boolean deprioritize,
                                                             boolean forceOverrideAnnotation) {

    RowIcon icon = new RowIcon(baseMethod.getIcon(0), implemented ? AllIcons.Gutter.ImplementingMethod : AllIcons.Gutter.OverridingMethod);
    return createGenerateMethodElement(baseMethod, substitutor, icon, baseClass.getName(), new InsertHandler<LookupElement>() {
      @Override
      public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
        removeLookupString(context);

        final PsiClass parent = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiClass.class, false);
        if (parent == null) return;

        try (AccessToken ignored = generateDefaultMethods ? forceDefaultMethodsInside() : AccessToken.EMPTY_ACCESS_TOKEN) {
          List<PsiMethod> prototypes = OverrideImplementUtil.overrideOrImplementMethod(parent, baseMethod, false, forceOverrideAnnotation);
          insertGenerationInfos(context, OverrideImplementUtil.convert2GenerationInfos(prototypes));
        }
      }

    }, generateDefaultMethods, targetClass, priority, deprioritize);
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
          ContainerUtil.addIfNotNull(elements, member.getPsiMember());
        }
      }

      newInfos.get(0).positionCaret(context.getEditor(), true);
      GlobalInspectionContextBase.cleanupElements(context.getProject(), null, elements.toArray(PsiElement.EMPTY_ARRAY));
    }
  }

  private static LookupElement createGenerateMethodElement(PsiMethod prototype,
                                                           PsiSubstitutor substitutor,
                                                           Icon icon,
                                                           String typeText, InsertHandler<LookupElement> insertHandler,
                                                           boolean generateDefaultMethod,
                                                           PsiClass targetClass, int priority, boolean deprioritize) {
    String methodName = prototype.getName();

    String visibility = VisibilityUtil.getVisibilityModifier(prototype.getModifierList());
    String modifiers = (visibility == PsiModifier.PACKAGE_LOCAL || visibility == PsiModifier.PUBLIC && targetClass.isInterface() ? "" : visibility + " ");
    if (generateDefaultMethod) {
      modifiers = "default " + modifiers;
    }

    PsiType type = substitutor.substitute(prototype.getReturnType());
    String signature = modifiers + (type == null ? "" : type.getPresentableText() + " ") + methodName;

    String parameters = "(" + StringUtil.join(prototype.getParameterList().getParameters(),
                                              p -> getShortParameterName(substitutor, p) + " " + p.getName(),
                                              ", ") + ")";

    String depriorizationPrefix = (deprioritize) ? " " : "";
    String overrideSignature = depriorizationPrefix + "@Override " + signature; // leading space to make it a middle match, under all annotation suggestions
    LookupElementBuilder element = LookupElementBuilder.create(prototype, " " + signature).
      withPresentableText(signature).
      withLookupString(overrideSignature).
      withLookupString(" " + signature).withLookupString(methodName).withInsertHandler(insertHandler).
      appendTailText(parameters, false).appendTailText(" {...}", true).withTypeText(typeText).withIcon(icon);
    if (prototype.isDeprecated()) {
      element = element.withStrikeoutness(true);
    }
    element.putUserData(GENERATE_ELEMENT, true);
    return PrioritizedLookupElement.withPriority(element, priority);
  }

  @NotNull
  private static String getShortParameterName(PsiSubstitutor substitutor, PsiParameter p) {
    return PsiNameHelper.getShortClassName(substitutor.substitute(p.getType()).getPresentableText(false));
  }

  private static class NoMiddleMatchesAfterSpace extends CamelHumpMatcher {
    NoMiddleMatchesAfterSpace(String prefix) {
      super(prefix);
    }

    @Override
    public boolean prefixMatches(@NotNull LookupElement element) {
      if (!super.prefixMatches(element)) return false;

      if (!myPrefix.contains(" ")) return true;

      String signature = element.getLookupString();
      FList<TextRange> fragments = matchingFragments(signature);
      return fragments == null || fragments.stream().noneMatch(f -> isMiddleMatch(signature, f));

    }

    private static boolean isMiddleMatch(String signature, TextRange fragment) {
      int start = fragment.getStartOffset();
      return start > 0 &&
             Character.isJavaIdentifierPart(signature.charAt(start)) &&
             Character.isJavaIdentifierPart(signature.charAt(start - 1));
    }
  }

}
