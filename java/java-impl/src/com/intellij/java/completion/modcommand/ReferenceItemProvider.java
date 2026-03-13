// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.JavaTailTypes;
import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.codeInsight.completion.CheckInitialized;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.JavaClassNameCompletionContributor;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.JavaFrontendCompletionUtil;
import com.intellij.codeInsight.completion.JavaMemberNameCompletionContributor;
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement;
import com.intellij.codeInsight.completion.JavaSmartCompletionContributor;
import com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor;
import com.intellij.codeInsight.completion.scope.CompletionElement;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModLaunchEditorAction;
import com.intellij.modcompletion.CommonCompletionItem;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiJavaCodeReferenceCodeFragment;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.ResolveState;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ElementExtractorFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.siyeh.ig.psiutils.JavaDeprecationUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.patterns.PsiJavaPatterns.virtualFile;

@NotNullByDefault
final class ReferenceItemProvider extends JavaModCompletionItemProvider {
  private static final ElementPattern<PsiElement> TOP_LEVEL_VAR_IN_MODULE = psiElement().withSuperParent(3, PsiJavaFile.class)
    .inVirtualFile(virtualFile().withName("module-info.java"));
  private static final ElementPattern<PsiElement> INSIDE_TYPECAST_EXPRESSION = psiElement().withParent(
    psiElement(PsiReferenceExpression.class).afterLeaf(
      psiElement().withText(")").withParent(PsiTypeCastExpression.class)));
  private static final ElementPattern<PsiElement> INSIDE_CONSTRUCTOR = psiElement().inside(psiMethod().constructor(true));
  private static final ElementPattern<PsiElement> AFTER_NEW = psiElement().afterLeaf(psiElement().withText(JavaKeywords.NEW));
  
  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    PsiElement position = context.getPosition();
    if (!(position.getParent() instanceof PsiJavaCodeReferenceElement ref)) return;
    if (TOP_LEVEL_VAR_IN_MODULE.accepts(position) ||
        JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN.accepts(position)) {
      return;
    }
    Set<ExpectedTypeInfo> expectedTypes = ContainerUtil.newHashSet(
      JavaSmartCompletionContributor.getExpectedTypes(position, context.isSmart()));
    ElementFilter filter = JavaCompletionContributor.getReferenceFilter(position);
    if (filter == null) return;
    if (context.invocationCount() <= 1 && JavaClassNameCompletionContributor.AFTER_NEW.accepts(position)) {
      filter = new AndFilter(filter, new ElementFilter() {
        @Override
        public boolean isAcceptable(Object element, @Nullable PsiElement context) {
          return !JavaPsiClassReferenceElement.isInaccessibleConstructorSuggestion(position, ObjectUtils.tryCast(element, PsiClass.class));
        }

        @Override
        public boolean isClassAcceptable(Class<?> hintClass) {
          return true;
        }
      });
    }

    boolean smart = context.isSmart();
    if (smart) {
      if (INSIDE_TYPECAST_EXPRESSION.accepts(position) || inCastContext(context)) {
        return;
      }

      ElementFilter smartRestriction = ReferenceExpressionCompletionContributor.getReferenceFilter(position, false);
      if (smartRestriction != TrueFilter.INSTANCE) {
        filter = new AndFilter(filter, smartRestriction);
      }
    }

    boolean inSwitchLabel = JavaCompletionContributor.IN_SWITCH_LABEL.accepts(position);
    ModNavigatorTailType forcedTail = getTailType(smart, inSwitchLabel, position);

    if (INSIDE_CONSTRUCTOR.accepts(position) &&
        (context.invocationCount() <= 1 || CheckInitialized.isInsideConstructorCall(position))) {
      filter = new AndFilter(filter, new CheckInitialized(position));
    }
    PsiFile originalFile = context.getOriginalFile();

    boolean first = context.invocationCount() <= 1;
    boolean instantiableOnly = AFTER_NEW.accepts(position);
    JavaCompletionProcessor.Options options =
      JavaCompletionProcessor.Options.DEFAULT_OPTIONS
        .withCheckAccess(first)
        .withFilterStaticAfterInstance(first)
        .withInstantiableOnly(instantiableOnly)
        .withShowInstanceInStaticContext(!first && !smart);

    processJavaReference(position, ref, new ElementExtractorFilter(filter),
                         options, context, element -> {
        if (forcedTail != null && element instanceof CommonCompletionItem commonItem) {
          element = commonItem.withTail(forcedTail);
        }

        if (inSwitchLabel && !smart && element instanceof CommonCompletionItem commonItem) {
          element = commonItem.adjustIndent();
        }
        //if (originalFile instanceof PsiJavaCodeReferenceCodeFragment fragment &&
        //    !fragment.isClassesAccepted() && item != null) {
        //  item.setTailType(TailTypes.noneType());
        //}
        //if (item instanceof JavaMethodCallElement call) {
        //  PsiMethod method = call.getObject();
        //  if (method.getTypeParameters().length > 0) {
        //    PsiType returned = TypeConversionUtil.erasure(method.getReturnType());
        //    ExpectedTypeInfo matchingExpectation = returned == null ? null : ContainerUtil.find(
        //      expectedTypes, info -> info.getDefaultType().isAssignableFrom(returned) || 
        //                             AssignableFromFilter.isAcceptable(method, position, info.getDefaultType(), call.getSubstitutor()));
        //    if (matchingExpectation != null) {
        //      call.setInferenceSubstitutorFromExpectedType(position, matchingExpectation.getDefaultType());
        //    }
        //  }
        //}
        sink.accept(element);

        CommonCompletionItem firstArrayElement = accessFirstElement(element);
        if (firstArrayElement != null) {
          sink.accept(firstArrayElement);
        }
      });
  }

  private static @Nullable CommonCompletionItem accessFirstElement(ModCompletionItem item) {
    if (item.contextObject() instanceof PsiLocalVariable variable) {
      final PsiType type = variable.getType();
      final PsiExpression expression = variable.getInitializer();
      if (type instanceof PsiArrayType arrayType && expression instanceof PsiNewExpression newExpression) {
        final PsiExpression[] dimensions = newExpression.getArrayDimensions();
        if (dimensions.length == 1 && "1".equals(dimensions[0].getText()) && newExpression.getArrayInitializer() == null) {
          final String text = variable.getName() + "[0]";
          return new CommonCompletionItem(text)
            .withPresentation(new ModCompletionItemPresentation(MarkupText.plainText(text))
                                .withMainIcon(() -> variable.getIcon(Iconable.ICON_FLAG_VISIBILITY))
                                .withDetailText(JavaModCompletionUtils.typeMarkup(arrayType.getComponentType())));
        }
      }
    }
    return null;
  }

  private static boolean inCastContext(CompletionContext context) {
    PsiElement position = context.getPosition();
    PsiElement parent = getParenthesisOwner(position);
    if (parent instanceof PsiTypeCastExpression) return true;
    if (parent instanceof PsiParenthesizedExpression) {
      return context.getOffset() == position.getTextRange().getStartOffset();
    }
    return false;
  }

  private static @Nullable PsiElement getParenthesisOwner(PsiElement position) {
    PsiElement lParen = PsiTreeUtil.prevVisibleLeaf(position);
    return lParen == null || !lParen.textMatches("(") ? null : lParen.getParent();
  }

  private static @Nullable ModNavigatorTailType getTailType(boolean smart, boolean inSwitchLabel, PsiElement position) {
    if (!smart && inSwitchLabel) {
      if (position instanceof PsiClass) {
        return ModNavigatorTailType.insertSpaceType();
      }
      return JavaTailTypes.forSwitchLabel(Objects.requireNonNull(PsiTreeUtil.getParentOfType(position, PsiSwitchBlock.class)));
    }
    if (!smart && shouldInsertSemicolon(position)) {
      return ModNavigatorTailType.semicolonType();
    }
    return null;
  }

  private static boolean shouldInsertSemicolon(PsiElement position) {
    return position.getParent() instanceof PsiMethodReferenceExpression &&
           JavaFrontendCompletionUtil.insertSemicolon(position.getParent().getParent());
  }

  private static List<PsiType> getQualifierCastTypes(PsiJavaReference javaReference, int invocationCount) {
    if (javaReference instanceof PsiReferenceExpression refExpr) {
      PsiExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier != null) {
        Project project = qualifier.getProject();
        return GuessManager.getInstance(project).getControlFlowExpressionTypeConjuncts(qualifier, invocationCount > 1);
      }
    }
    return Collections.emptyList();
  }

  private static void processJavaReference(PsiElement element,
                                           PsiJavaCodeReferenceElement javaReference,
                                           ElementFilter elementFilter,
                                           JavaCompletionProcessor.Options options,
                                           CompletionContext parameters,
                                           ModCompletionResult sink) {
    Condition<String> nameCondition = s -> true;
    JavaCompletionProcessor processor = new JavaCompletionProcessor(element, elementFilter, options, nameCondition);
    PsiType plainQualifier = processor.getQualifierType();

    List<PsiType> runtimeQualifiers = getQualifierCastTypes(javaReference, parameters.invocationCount());
    if (!runtimeQualifiers.isEmpty()) {
      PsiType[] conjuncts = JBIterable.of(plainQualifier).append(runtimeQualifiers).toArray(PsiType.EMPTY_ARRAY);
      PsiType composite = PsiIntersectionType.createIntersection(false, conjuncts);
      PsiElement ctx = JavaCompletionUtil.createContextWithXxxVariable(element, composite);
      javaReference = (PsiReferenceExpression)JavaPsiFacade.getElementFactory(parameters.getProject())
        .createExpressionFromText("xxx.xxx", ctx);
      processor.setQualifierType(composite);
    }

    javaReference.processVariants(processor);

    List<PsiTypeCompletionItem> castItems = ContainerUtil.map(runtimeQualifiers, q -> new PsiTypeCompletionItem(q, q.getPresentableText()));

    boolean pkgContext = JavaCompletionUtil.inSomePackage(element);

    PsiClass qualifierClass = PsiUtil.resolveClassInClassTypeOnly(plainQualifier);
    boolean honorExcludes = qualifierClass == null || !JavaCompletionUtil.isInExcludedPackage(qualifierClass, false);

    Set<PsiType> expectedTypes = ObjectUtils.coalesce(JavaCompletionUtil.getExpectedTypes(parameters), Collections.emptySet());

    Set<PsiMember> mentioned = new HashSet<>();
    //JavaCompletionUtil.JavaLookupElementHighlighter highlighter = getHighlighterForPlace(element, parameters.getOriginalFile().getVirtualFile());
    for (CompletionElement completionElement : processor.getResults()) {
      for (ModCompletionItem item : createLookupElements(completionElement, javaReference)) {
        //item.putUserData(QUALIFIER_TYPE_ATTR, plainQualifier);
        Object o = item.contextObject();
        if (o instanceof PsiClass psiClass) {
          PsiClass specifiedQualifierClass = javaReference.isQualified() ? qualifierClass : (psiClass).getContainingClass();
          if (!JavaCompletionUtil.isSourceLevelAccessible(element, psiClass, pkgContext, specifiedQualifierClass)) {
            continue;
          }
        }
        if (o instanceof PsiMember member) {
          if (honorExcludes && JavaCompletionUtil.isInExcludedPackage(member, true)) {
            continue;
          }
          mentioned.add(CompletionUtil.getOriginalOrSelf(member));
        }
        PsiTypeCompletionItem qualifierCast = null;
        PsiMember member = ObjectUtils.tryCast(item.contextObject(), PsiMember.class);
        if (member != null) {
          qualifierCast = ContainerUtil.find(castItems, c -> {
            PsiType castType = c.getType();
            return JavaCompletionUtil.shouldCast(plainQualifier, processor, expectedTypes, castType, member);
          });
        }
        // TODO: support VariableCompletionItem, etc.
        if (qualifierCast != null && item instanceof CommonCompletionItem cci) item = castQualifier(cci, qualifierCast);
        sink.accept(item);
        //set.add(highlighter.highlightIfNeeded(qualifierCast != null ? qualifierCast.getType() : plainQualifier, item, o));
      }
    }
    
    PsiElement refQualifier = javaReference.getQualifier();
    if (refQualifier == null && PsiTreeUtil.getParentOfType(element, PsiPackageStatement.class, PsiImportStatementBase.class) == null) {
      ModJavaStaticMemberProcessor memberProcessor = new ModJavaStaticMemberProcessor(parameters);
      memberProcessor.processMembersOfRegisteredClasses(nameCondition, (member, psiClass) -> {
        if (!mentioned.contains(member) && processor.satisfies(member, ResolveState.initial())) {
          ModCompletionItem item = memberProcessor.createCompletionItem(member, psiClass, true);
          if (item != null) {
            sink.accept(item);
          }
        }
      });
    }
    //else if (refQualifier instanceof PsiSuperExpression && ((PsiSuperExpression)refQualifier).getQualifier() == null) {
    //  set.addAll(SuperCalls.suggestQualifyingSuperCalls(element, javaReference, elementFilter, options, nameCondition));
    //}
  }

  private static @Unmodifiable List<ModCompletionItem> createLookupElements(CompletionElement completionElement, PsiJavaReference reference) {
    Object completion = completionElement.getElement();
    assert !(completion instanceof LookupElement);

    if (reference instanceof PsiJavaCodeReferenceElement ref) {
      if (completion instanceof PsiMethod method &&
          ref.getParent() instanceof PsiImportStaticStatement) {
        String parameters = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
                                              PsiFormatUtilBase.SHOW_PARAMETERS,
                                              PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE);
        ModCompletionItemPresentation presentation = 
          new ModCompletionItemPresentation(
            MarkupText.plainText(method.getName()).concat(parameters, MarkupText.Kind.GRAYED))
            .withMainIcon(() -> method.getIcon(Iconable.ICON_FLAG_VISIBILITY))
            .withDetailText(JavaModCompletionUtils.typeMarkup(method.getReturnType()));
        return List.of(new CommonCompletionItem(method.getName())
          .withObject(method)
          .withPresentation(presentation));
      }

      if (completion instanceof PsiClass cls) {
        List<ClassReferenceCompletionItem> classItems = NonImportedClassProvider.createClassLookupItems(
          CompletionUtil.getOriginalOrSelf(cls),
          JavaClassNameCompletionContributor.AFTER_NEW.accepts(reference),
          Predicates.alwaysTrue());
        return StreamEx.of(classItems).toFlatList(i -> ConstructorCallCompletionItem.tryWrap(i, reference.getElement()));
      }
    }

    PsiSubstitutor substitutor = completionElement.getSubstitutor();
    if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;
    if (completion instanceof PsiClass cls) {
      return ConstructorCallCompletionItem.tryWrap(new ClassReferenceCompletionItem(cls).withSubstitutor(substitutor), reference.getElement());
    }
    if (completion instanceof PsiMethod method) {
      if (reference instanceof PsiMethodReferenceExpression mr) {
        String lookup = method.isConstructor() ? JavaKeywords.NEW : method.getName();
        MarkupText text = MarkupText.plainText(lookup).highlightAll(
          JavaDeprecationUtils.isDeprecated(method, mr) ? MarkupText.Kind.STRIKEOUT : MarkupText.Kind.NORMAL);
        return List.of(new CommonCompletionItem(lookup)
                         .withObject(method)
                         .withPresentation(new ModCompletionItemPresentation(text)
                                             .withMainIcon(() -> method.getIcon(Iconable.ICON_FLAG_VISIBILITY))));
      }

      return List.of(new MethodCallCompletionItem(method)
        .withQualifierSubstitutor(substitutor)
        .withForcedQualifier(completionElement.getQualifierText()));
    }
    if (completion instanceof PsiVariable var) {
      if (completion instanceof PsiEnumConstant enumConstant &&
          PsiTreeUtil.isAncestor(enumConstant.getArgumentList(), reference.getElement(), true)) {
        return List.of();
      }
      return List.of(new VariableCompletionItem(var).withSubstitutor(substitutor).qualifyIfNeeded(reference, null));
    }
    if (completion instanceof PsiPackage pkg) {
      PsiElement context = reference.getElement();
      PsiFile file = context.getContainingFile();
      boolean inExportsOpens = context.getParent() instanceof PsiPackageAccessibilityStatement;
      boolean addDot = !inExportsOpens && (!(file instanceof PsiJavaCodeReferenceCodeFragment ref) || ref.isClassesAccepted());
      return List.of(new CommonCompletionItem(StringUtil.notNullize(pkg.getName()))
                       .withObject(pkg)
                       .withTail(addDot ? ModNavigatorTailType.dotType() : ModNavigatorTailType.noneType())
                       .withAdditionalUpdater(((completionStart, updater) -> updater.editorAction(ModLaunchEditorAction.ACTION_CODE_COMPLETION, true)))
                       .withPresentation(new ModCompletionItemPresentation(MarkupText.plainText(pkg.getName()+(addDot?".":"")))
                                           .withMainIcon(() -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Package))));
    }

    return List.of();
  }
  
  private static CommonCompletionItem castQualifier(CommonCompletionItem item, PsiTypeCompletionItem castTypeItem) {
    return item.withAdditionalUpdater((completionStart, updater) -> {
      Document document = updater.getDocument();
      PsiDocumentManager.getInstance(updater.getProject()).commitDocument(document);
      PsiFile file = updater.getPsiFile();
      PsiJavaCodeReferenceElement ref =
        PsiTreeUtil.findElementOfClassAtOffset(file, completionStart, PsiJavaCodeReferenceElement.class, false);
      if (ref != null) {
        PsiElement qualifier = ref.getQualifier();
        if (qualifier != null) {
          CommonCodeStyleSettings settings = CodeStyle.getLanguageSettings(file, JavaLanguage.INSTANCE);

          String parenSpace = settings.SPACE_WITHIN_PARENTHESES ? " " : "";
          document.insertString(qualifier.getTextRange().getEndOffset(), parenSpace + ")");

          String spaceWithin = settings.SPACE_WITHIN_CAST_PARENTHESES ? " " : "";
          String prefix = "(" + parenSpace + "(" + spaceWithin;
          String spaceAfter = settings.SPACE_AFTER_TYPE_CAST ? " " : "";
          int exprStart = qualifier.getTextRange().getStartOffset();
          document.insertString(exprStart, prefix + spaceWithin + ")" + spaceAfter);

          ActionContext ctx = new ActionContext(updater.getProject(), updater.getPsiFile(),
                                                    updater.getCaretOffset(), TextRange.from(updater.getCaretOffset(), 0),
                                                    null);
          ModCommand command = castTypeItem.perform(ctx, ModCompletionItem.DEFAULT_INSERTION_CONTEXT);
          ModCommandExecutor.getInstance().executeForFileCopy(command, updater.getPsiFile());
        }
      }
    });
    //
    //  @Override
    //  public void renderElement(@NotNull LookupElementPresentation presentation) {
    //    super.renderElement(presentation);
    //
    //    presentation.appendTailText(" on " + castTypeItem.getType().getPresentableText(), true);
    //  }
    //};
  }
}
