// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.generation.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.icons.AllIcons;
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
        String fileText = position.getContainingFile().getText();
        result = result.withPrefixMatcher(new NoMiddleMatchesAfterSpace(
          fileText.substring(modifierList.getTextRange().getStartOffset(), parameters.getOffset())));
      }
      suggestGeneratedMethods(result, position, modifierList);
    } else if (isTypingAnnotationForNewMember(position)) {
      PsiAnnotation annotation = Objects.requireNonNull(PsiTreeUtil.getParentOfType(position, PsiAnnotation.class));
      int annoStart = annotation.getTextRange().getStartOffset();

      result.addElement(itemWithOverrideImplementDialog(annoStart));

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

  @NotNull
  private static LookupElementBuilder itemWithOverrideImplementDialog(int annoStart) {
    return LookupElementBuilder.create("Override/Implement methods...").withInsertHandler((context, item) -> {
      context.getDocument().deleteString(annoStart, context.getTailOffset());
      context.commitDocument();
      context.setAddCompletionChar(false);
      context.setLaterRunnable(() -> {
        new OverrideMethodsHandler().invoke(context.getProject(), context.getEditor(), context.getFile());
      });
    });
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
          result.addElement(createGenerateMethodElement(prototype, PsiSubstitutor.EMPTY, icon, "", new InsertHandler<LookupElement>() {
            @Override
            public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
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

  private static void addSuperSignatureElements(PsiClass parent, boolean implemented, CompletionResultSet result, Set<? super MethodSignature> addedSignatures, boolean generateDefaultMethods) {
    for (CandidateInfo candidate : OverrideImplementExploreUtil.getMethodsToOverrideImplement(parent, implemented)) {
      PsiMethod baseMethod = (PsiMethod)candidate.getElement();
      PsiClass baseClass = baseMethod.getContainingClass();
      PsiSubstitutor substitutor = candidate.getSubstitutor();
      if (!baseMethod.isConstructor() && baseClass != null && addedSignatures.add(baseMethod.getSignature(substitutor))) {
        result.addElement(createOverridingLookupElement(implemented, baseMethod, baseClass, substitutor, generateDefaultMethods, parent));
      }
    }
  }

  private static LookupElement createOverridingLookupElement(boolean implemented,
                                                             PsiMethod baseMethod,
                                                             PsiClass baseClass, PsiSubstitutor substitutor, boolean generateDefaultMethods, PsiClass targetClass) {

    RowIcon icon = IconManager
      .getInstance().createRowIcon(baseMethod.getIcon(0), implemented ? AllIcons.Gutter.ImplementingMethod : AllIcons.Gutter.OverridingMethod);
    return createGenerateMethodElement(baseMethod, substitutor, icon, baseClass.getName(), new InsertHandler<LookupElement>() {
      @Override
      public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
        removeLookupString(context);

        final PsiClass parent = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiClass.class, false);
        if (parent == null) return;

        if (GenerateEqualsHandler.hasNonStaticFields(parent) && generateByWizards(context)) {
          return;
        }

        try (AccessToken ignored = generateDefaultMethods ? forceDefaultMethodsInside() : AccessToken.EMPTY_ACCESS_TOKEN) {
          List<PsiMethod> prototypes = OverrideImplementUtil.overrideOrImplementMethod(parent, baseMethod, false);
          insertGenerationInfos(context, OverrideImplementUtil.convert2GenerationInfos(prototypes));
        }
      }

      private boolean generateByWizards(@NotNull InsertionContext context) {
        PsiFile file = context.getFile();
        if (MethodUtils.isEquals(baseMethod) || MethodUtils.isHashCode(baseMethod)) {
          context.setAddCompletionChar(false);
          context.setLaterRunnable(() -> new GenerateEqualsHandler().invoke(context.getProject(), context.getEditor(), file));
          return true;
        }

        if (MethodUtils.isToString(baseMethod)) {
          context.setAddCompletionChar(false);
          context.setLaterRunnable(() -> new GenerateToStringActionHandlerImpl().invoke(context.getProject(), context.getEditor(), file));
          return true;
        }

        return false;
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
                                                           PsiClass targetClass) {
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

    String overrideSignature = " @Override " + signature; // leading space to make it a middle match, under all annotation suggestions
    LookupElementBuilder element = LookupElementBuilder.create(prototype, signature).withLookupString(methodName).
      withLookupString(signature).withLookupString(overrideSignature).withInsertHandler(insertHandler).
      appendTailText(parameters, false).appendTailText(" {...}", true).withTypeText(typeText).withIcon(icon);
    if (prototype.isDeprecated()) {
      element = element.withStrikeoutness(true);
    }
    element.putUserData(GENERATE_ELEMENT, true);
    return PrioritizedLookupElement.withPriority(element, -1);
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
