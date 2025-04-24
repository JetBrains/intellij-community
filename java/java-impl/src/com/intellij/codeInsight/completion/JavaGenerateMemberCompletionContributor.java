// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.command.configuration.ApplicationCommandCompletionService;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.generation.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.SmartList;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import com.intellij.util.text.CharArrayUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.GenerateToStringActionHandlerImpl;
import org.jetbrains.java.generate.exception.GenerateCodeException;

import javax.swing.*;
import java.util.*;
import java.util.function.Consumer;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public final class JavaGenerateMemberCompletionContributor {
  public static final Key<Boolean> GENERATE_ELEMENT = Key.create("GENERATE_ELEMENT");

  public static void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.BASIC && parameters.getCompletionType() != CompletionType.SMART) {
      return;
    }
    PsiElement position = parameters.getPosition();
    if (DumbService.getInstance(position.getProject()).isDumb()) {
      return;
    }

    if (psiElement(PsiIdentifier.class).withParents(PsiJavaCodeReferenceElement.class, PsiTypeElement.class, PsiClass.class).
      andNot(JavaKeywordCompletion.AFTER_DOT).accepts(position)) {
      PsiElement prevLeaf = PsiTreeUtil.prevVisibleLeaf(position);
      PsiModifierList modifierList = PsiTreeUtil.getParentOfType(prevLeaf, PsiModifierList.class);
      if (modifierList != null) {
        String fileText = position.getContainingFile().getText();
        result = result.withPrefixMatcher(new NoMiddleMatchesAfterSpace(
          fileText.substring(modifierList.getTextRange().getStartOffset(), parameters.getOffset())));
      }
      suggestGeneratedMethods(result, position, modifierList);
    } else if (isTypingAnnotationForNewMember(position)) {
      PsiAnnotation annotation = Objects.requireNonNull(PsiTreeUtil.getParentOfType(position, PsiAnnotation.class));
      int annoStart = annotation.getTextRange().getStartOffset();

      result.addElement(itemWithOverrideImplementDialog());

      suggestGeneratedMethods(
        result.withPrefixMatcher(new NoMiddleMatchesAfterSpace(annotation.getText().substring(0, parameters.getOffset() - annoStart))),
        position,
        (PsiModifierList)annotation.getParent());
    }

  }

  private static boolean isTypingAnnotationForNewMember(PsiElement position) {
    if (psiElement(PsiIdentifier.class)
      .withParents(PsiJavaCodeReferenceElement.class, PsiAnnotation.class, PsiModifierList.class).accepts(position)) {
      PsiElement parent = Objects.requireNonNull(PsiTreeUtil.getParentOfType(position, PsiModifierList.class)).getParent();
      if (parent instanceof PsiClass) {
        return true;
      }

      if (parent instanceof PsiMethod || parent instanceof PsiField) {
        PsiAnnotation anno = Objects.requireNonNull(PsiTreeUtil.getParentOfType(position, PsiAnnotation.class));
        return anno.getTextRange().getStartOffset() == parent.getTextRange().getStartOffset() && isFollowedByEol(anno);
      }
    }
    return false;
  }

  private static boolean isFollowedByEol(PsiAnnotation anno) {
    CharSequence fileText = anno.getContainingFile().getViewProvider().getContents();
    int afterAnno = CharArrayUtil.shiftForward(fileText, anno.getTextRange().getEndOffset(), " \t");
    return fileText.length() > afterAnno && fileText.charAt(afterAnno) == '\n';
  }

  private static @NotNull LookupElement itemWithOverrideImplementDialog() {
    LookupElementBuilder element =
      LookupElementBuilder.create(JavaBundle.message("completion.override.implement.methods")).withLookupString("Override")
        .withInsertHandler((context, item) -> {
          PsiAnnotation annotation =
            PsiTreeUtil.getParentOfType(context.getFile().findElementAt(context.getStartOffset()), PsiAnnotation.class);
          if (annotation != null) {
            context.getDocument().deleteString(annotation.getTextRange().getStartOffset(), context.getTailOffset());
            context.commitDocument();
          }
          context.setAddCompletionChar(false);
          context.setLaterRunnable(() -> {
            new OverrideMethodsHandler().invoke(context.getProject(), context.getEditor(), context.getFile());
          });
        });
    return PrioritizedLookupElement.withPriority(element, -1);
  }

  private static void suggestGeneratedMethods(CompletionResultSet result, PsiElement position, @Nullable PsiModifierList modifierList) {
    PsiClass parent = CompletionUtil.getOriginalElement(Objects.requireNonNull(PsiTreeUtil.getParentOfType(position, PsiClass.class)));
    if (parent != null) {
      Set<MethodSignature> addedSignatures = new HashSet<>();
      addGetterSetterElements(result, parent, addedSignatures);
      boolean generateDefaultMethods = modifierList != null && modifierList.hasModifierProperty(PsiModifier.DEFAULT);
      addSuperSignatureElements(parent, true, result, addedSignatures, generateDefaultMethods);
      addSuperSignatureElements(parent, false, result, addedSignatures, generateDefaultMethods);
    }
  }

  private static void addGetterSetterElements(CompletionResultSet result, PsiClass parent, Set<? super MethodSignature> addedSignatures) {
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
      catch (GenerateCodeException ignore) { }
      for (final PsiMethod prototype : prototypes) {
        PsiMethod existingMethod = parent.findMethodBySignature(prototype, false);
        if ((existingMethod == null || existingMethod instanceof SyntheticElement) &&
            addedSignatures.add(prototype.getSignature(PsiSubstitutor.EMPTY))) {
          Icon icon = prototype.getIcon(Iconable.ICON_FLAG_VISIBILITY);
          result.addElement(createGenerateMethodElement(prototype, PsiSubstitutor.EMPTY, icon, "", new InsertHandler<>() {
            @Override
            public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
              removeLookupString(context);

              insertGenerationInfos(context, Collections.singletonList(new PsiGenerationInfo<>(prototype)));
            }
          }, false, false, parent));

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

  private static void addSuperSignatureElements(PsiClass parent, boolean implemented, CompletionResultSet result, Set<? super MethodSignature> addedSignatures, boolean generateDefaultMethods) {
    for (CandidateInfo candidate : OverrideImplementExploreUtil.getMethodsToOverrideImplement(parent, implemented)) {
      PsiMethod baseMethod = (PsiMethod)candidate.getElement();
      PsiClass baseClass = baseMethod.getContainingClass();
      PsiSubstitutor substitutor = candidate.getSubstitutor();
      if (!baseMethod.isConstructor() && baseClass != null && addedSignatures.add(baseMethod.getSignature(substitutor))) {
        result.addElement(
          createOverridingLookupElement(implemented, baseMethod, baseClass, substitutor, generateDefaultMethods, parent, null));
        if (GenerateEqualsHandler.hasNonStaticFields(parent) && !ApplicationCommandCompletionService.getInstance().commandCompletionEnabled()) {
          if (MethodUtils.isEquals(baseMethod) || MethodUtils.isHashCode(baseMethod)) {
            result.addElement(
              createOverridingLookupElement(implemented, baseMethod, baseClass, substitutor, generateDefaultMethods, parent, context -> {
                new GenerateEqualsHandler().invoke(context.getProject(), context.getEditor(), context.getFile());
              }));
          }
          else if (MethodUtils.isToString(baseMethod)) {
            result.addElement(
              createOverridingLookupElement(implemented, baseMethod, baseClass, substitutor, generateDefaultMethods, parent, context -> {
                new GenerateToStringActionHandlerImpl().invoke(context.getProject(), context.getEditor(), context.getFile());
              }));
          }
        }
      }
    }
  }

  private static LookupElement createOverridingLookupElement(boolean implemented,
                                                             PsiMethod baseMethod,
                                                             PsiClass baseClass,
                                                             PsiSubstitutor substitutor,
                                                             boolean generateDefaultMethods,
                                                             PsiClass targetClass,
                                                             @Nullable Consumer<? super InsertionContext> wizardRunner) {

    RowIcon icon = IconManager
      .getInstance()
      .createRowIcon(baseMethod.getIcon(0), implemented ? AllIcons.Gutter.ImplementingMethod : AllIcons.Gutter.OverridingMethod);
    InsertHandler<LookupElement> handler;
    if (wizardRunner != null) {
      handler = new InsertHandler<>() {
        @Override
        public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
          removeLookupString(context);

          context.setAddCompletionChar(false);
          context.setLaterRunnable(() -> wizardRunner.accept(context));
        }
      };
    }
    else {
      handler = new InsertHandler<>() {
        @Override
        public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
          removeLookupString(context);

          final PsiClass parent =
            PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiClass.class, false);
          if (parent == null) return;

          try (AccessToken ignored = generateDefaultMethods ? forceDefaultMethodsInside() : AccessToken.EMPTY_ACCESS_TOKEN) {
            List<PsiMethod> prototypes = OverrideImplementUtil.overrideOrImplementMethod(parent, baseMethod, false);
            insertGenerationInfos(context, OverrideImplementUtil.convert2GenerationInfos(prototypes));
          }
        }
      };
    }
    return createGenerateMethodElement(baseMethod, substitutor, icon, baseClass.getName(), handler, wizardRunner != null,
                                       generateDefaultMethods, targetClass);
  }

  private static AccessToken forceDefaultMethodsInside() {
    CommandProcessor instance = CommandProcessor.getInstance();
    String commandName = instance.getCurrentCommandName();
    instance.setCurrentCommandName(JavaBundle.message("generate.members.implement.command"));
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
      ApplicationManager.getApplication().invokeLater(
        () -> GlobalInspectionContextBase.cleanupElements(
          context.getProject(), null, elements.stream().filter(e -> e.isValid()).toArray(PsiElement[]::new)));
    }
  }

  private static LookupElement createGenerateMethodElement(PsiMethod prototype,
                                                           PsiSubstitutor substitutor,
                                                           Icon icon,
                                                           String typeText,
                                                           InsertHandler<LookupElement> insertHandler,
                                                           boolean generateByWizard,
                                                           boolean generateDefaultMethod,
                                                           PsiClass targetClass) {
    String methodName = prototype.getName();

    String visibility = VisibilityUtil.getVisibilityModifier(prototype.getModifierList());
    String modifiers = (visibility.equals(PsiModifier.PACKAGE_LOCAL) || visibility.equals(PsiModifier.PUBLIC) && targetClass.isInterface() ? "" : visibility + " ");
    if (generateDefaultMethod) {
      modifiers = "default " + modifiers;
    }

    PsiType type = substitutor.substitute(prototype.getReturnType());
    String signature = modifiers + (type == null ? "" : type.getPresentableText() + " ") + methodName;

    String parameters = "(" + StringUtil.join(prototype.getParameterList().getParameters(),
                                              p -> getShortParameterName(substitutor, p) + " " + p.getName(),
                                              ", ") + ")";

    String overrideSignature = " @Override " + signature; // leading space to make it a middle match, under all annotation suggestions
    String tailText = " " + (generateByWizard ? JavaBundle.message("completion.generate.via.wizard") : "{...}");
    LookupElementBuilder element = LookupElementBuilder.create(prototype, signature).withLookupString(methodName).
      withLookupString(signature).withLookupString(overrideSignature).withInsertHandler(insertHandler).
      appendTailText(parameters, false).appendTailText(tailText, true).withTypeText(typeText).withIcon(icon);
    if (generateByWizard) {
      JavaMethodMergingContributor.disallowMerge(element);
    }
    if (prototype.isDeprecated()) {
      element = element.withStrikeoutness(true);
    }
    
    element.putUserData(GENERATE_ELEMENT, generateByWizard);
    return PrioritizedLookupElement.withPriority(element, -1);
  }

  private static @NotNull String getShortParameterName(PsiSubstitutor substitutor, PsiParameter p) {
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
      return fragments == null || !ContainerUtil.exists(fragments, f -> isMiddleMatch(signature, f));

    }

    private static boolean isMiddleMatch(String signature, TextRange fragment) {
      int start = fragment.getStartOffset();
      return start > 0 &&
             Character.isJavaIdentifierPart(signature.charAt(start)) &&
             Character.isJavaIdentifierPart(signature.charAt(start - 1));
    }
  }
}
