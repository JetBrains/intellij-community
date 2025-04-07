// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.Predicates;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.intellij.pom.java.LanguageLevel.JDK_11;
import static com.intellij.pom.java.LanguageLevel.JDK_1_9;
import static java.util.Objects.requireNonNull;

public final class WrapWithAdapterMethodCallFix extends PsiUpdateModCommandAction<PsiExpression> {
  abstract static class AbstractWrapper extends ArgumentFixerActionFactory {
    abstract boolean isApplicable(PsiElement context, PsiType inType, PsiType outType);

    @Override
    public boolean areTypesConvertible(final @NotNull PsiType exprType,
                                       final @NotNull PsiType parameterType,
                                       final @NotNull PsiElement context) {
      return parameterType.isConvertibleFrom(exprType) || isApplicable(context, exprType, parameterType);
    }

    @Override
    public IntentionAction createFix(final PsiExpressionList list, final int i, final PsiType toType) {
      return new MyMethodArgumentFix(list, i, toType, this).asIntention();
    }

    abstract String getText(PsiExpression element, PsiType type);
  }

  static class InstanceMethodFixer extends AbstractWrapper {
    @Override
    protected @Nullable PsiExpression getModifiedArgument(PsiExpression expression, PsiType toType)
      throws IncorrectOperationException {
      PsiMethod targetMethod = findOnlyMethod(expression.getType(), toType);
      if (targetMethod == null) return null;
      var replacement = (PsiMethodCallExpression)JavaPsiFacade.getElementFactory(expression.getProject())
        .createExpressionFromText("x." + targetMethod.getName() + "()", expression);
      requireNonNull(replacement.getMethodExpression().getQualifierExpression()).replace(expression);
      return replacement;
    }

    private static @Nullable PsiMethod findOnlyMethod(@Nullable PsiType inType, @NotNull PsiType outType) {
      if (!(inType instanceof PsiClassType)) return null;
      PsiClassType.ClassResolveResult result = ((PsiClassType)inType).resolveGenerics();
      PsiClass psiClass = result.getElement();
      if (psiClass == null || psiClass instanceof PsiTypeParameter) return null;
      return StreamEx.of(psiClass.getAllMethods())
        .collect(MoreCollectors.onlyOne(method -> {
          if (method.hasModifierProperty(PsiModifier.STATIC) || !method.hasModifierProperty(PsiModifier.PUBLIC)) return false;
          if (method.isConstructor()) return false;
          if (method.getName().equals("hashCode")) return false;
          if (!method.getParameterList().isEmpty()) return false;
          PsiType type = method.getReturnType();
          if (type == null || PsiTypes.voidType().equals(type)) return false;
          if (type instanceof PsiClassType) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) return false;
            var substitutor = TypeConversionUtil.getMaybeSuperClassSubstitutor(containingClass, psiClass, result.getSubstitutor());
            if (substitutor != null) {
              type = substitutor.substitute(type);
            }
          }
          return outType.isAssignableFrom(type);
        })).orElse(null);
    }

    @Override
    boolean isApplicable(PsiElement context, PsiType inType, PsiType outType) {
      return findOnlyMethod(inType, outType) != null;
    }

    @Override
    @Nullable String getText(PsiExpression expression, PsiType type) {
      PsiMethod method = findOnlyMethod(expression.getType(), type);
      if (method != null) {
        return method.getName() + "()";
      }
      return null;
    }
  }

  static class Wrapper extends AbstractWrapper {
    final Predicate<? super PsiType> myInTypeFilter;
    final Predicate<? super PsiType> myOutTypeFilter;
    final String myTemplate;

    /**
     * @param template      template for replacement (original expression is referenced as {@code {0}})
     * @param inTypeFilter  filter for input type (must return true if supplied type is acceptable as input type for this wrapper)
     * @param outTypeFilter quick filter for output type (must return true if supplied output type is acceptable for this wrapper).
     *                      It's allowed to check imprecisely (return true even if output type is not acceptable) as more
     *                      expensive type check will be performed automatically.
     */
    Wrapper(@NonNls String template, Predicate<? super PsiType> inTypeFilter, Predicate<? super PsiType> outTypeFilter) {
      myInTypeFilter = inTypeFilter;
      myOutTypeFilter = outTypeFilter;
      myTemplate = template;
    }

    @Override
    boolean isApplicable(PsiElement context, PsiType inType, PsiType outType) {
      if (inType == null ||
          outType == null ||
          inType.equals(PsiTypes.nullType()) ||
          !myInTypeFilter.test(inType) ||
          !myOutTypeFilter.test(outType)) {
        return false;
      }
      PsiType variableType = GenericsUtil.getVariableTypeByExpressionType(inType);
      if (LambdaUtil.notInferredType(variableType)) return false;
      if (variableType instanceof PsiDisjunctionType) {
        variableType = ((PsiDisjunctionType)variableType).getLeastUpperBound();
      }

      String typeText = variableType.getCanonicalText();
      // Empty text can be generated by PsiImmediateClassType if unresolved anonymous class is created like new X() {}
      if (typeText.isEmpty()) return false;
      if (variableType instanceof PsiClassType clsType &&
          !PsiNameHelper.getInstance(context.getProject()).isQualifiedName(clsType.rawType().getCanonicalText())) {
        // Wrong qualified name may happen e.g. if we depend on class file that contains a package name with keyword part
        // (probably compiled from non-Java language)
        return false;
      }
      PsiExpression replacement;
      try {
        replacement = createReplacement(context, "((" + typeText + ")null)");
      }
      catch (IncorrectOperationException ioe) {
        PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(variableType);
        throw new IncorrectOperationException(getExceptionMessage(variableType, aClass), (Throwable)ioe);
      }
      PsiDeclarationStatement declaration =
        JavaPsiFacade.getElementFactory(context.getProject()).createVariableDeclarationStatement("x", outType, replacement, context);
      PsiVariable var = ObjectUtils.tryCast(ArrayUtil.getFirstElement(declaration.getDeclaredElements()), PsiVariable.class);
      if (var == null) return false;
      PsiExpression initializer = var.getInitializer();
      if (initializer == null) return false;
      PsiType resultType = initializer.getType();
      return resultType != null && outType.isAssignableFrom(resultType);
    }

    private static @NotNull String getExceptionMessage(PsiType variableType, PsiClass aClass) {
      String message = "Cannot create expression for type " + variableType.getClass() + "\n"
                       + "Canonical text: " + variableType.getCanonicalText() + "\n"
                       + "Internal text: " + variableType.getInternalCanonicalText() + "\n";
      if (aClass != null) {
        message += "Class: " + aClass.getClass() + "|" + aClass.getQualifiedName() + "\n"
                   + "File: " + aClass.getContainingFile() + "\n";
      }
      if (variableType instanceof PsiClassReferenceType) {
        PsiJavaCodeReferenceElement reference = ((PsiClassReferenceType)variableType).getReference();
        message += "Reference: " + reference.getCanonicalText() + "\n"
                   + "Reference class: " + reference.getClass() + "\n"
                   + "Reference name: " + reference.getReferenceName() + "\n"
                   + "Reference qualifier: " + (reference.getQualifier() == null ? "(null)" : reference.getQualifier().getText()) + "\n"
                   + "Reference file: " + reference.getContainingFile();
      }
      return message;
    }

    @Override
    String getText(PsiExpression element, PsiType type) {
      return toString();
    }

    private @NotNull PsiExpression createReplacement(PsiElement context, @NonNls String replacement) {
      return JavaPsiFacade.getElementFactory(context.getProject()).createExpressionFromText(
        myTemplate.replace("{0}", replacement), context);
    }

    @Override
    protected @Nullable PsiExpression getModifiedArgument(final PsiExpression expression, final PsiType toType) throws IncorrectOperationException {
      if (isApplicable(expression, expression.getType(), toType)) {
        return (PsiExpression)JavaCodeStyleManager.getInstance(expression.getProject())
          .shortenClassReferences(createReplacement(expression, expression.getText()));
      }
      return null;
    }

    @Override
    public String toString() {
      return myTemplate.replace("{0}", "").replaceAll("\\b[a-z.]+\\.", "");
    }
  }

  private static final AbstractWrapper[] WRAPPERS = {
    new InstanceMethodFixer(),
    new Wrapper("new java.io.File({0})",
                inType -> inType.equalsToText(CommonClassNames.JAVA_LANG_STRING),
                outType -> outType.equalsToText(CommonClassNames.JAVA_IO_FILE)),
    new Wrapper("new java.lang.StringBuilder({0})",
                inType -> inType.equalsToText(CommonClassNames.JAVA_LANG_STRING),
                outType -> outType.equalsToText(CommonClassNames.JAVA_LANG_STRING_BUILDER)),
    new Wrapper("java.nio.file.Path.of({0})",
                inType -> inType.equalsToText(CommonClassNames.JAVA_LANG_STRING),
                outType -> outType.equalsToText("java.nio.file.Path") && isAppropriateLanguageLevel(outType, level -> level.isAtLeast(JDK_11))),
    new Wrapper("java.nio.file.Paths.get({0})",
                inType -> inType.equalsToText(CommonClassNames.JAVA_LANG_STRING),
                outType -> outType.equalsToText("java.nio.file.Path") && isAppropriateLanguageLevel(outType, level -> level.isLessThan(JDK_11))),
    new Wrapper("java.util.Arrays.asList({0})",
                inType -> inType instanceof PsiArrayType && ((PsiArrayType)inType).getComponentType() instanceof PsiClassType,
                outType -> InheritanceUtil.isInheritor(outType, CommonClassNames.JAVA_LANG_ITERABLE) &&
                           isAppropriateLanguageLevel(outType, l -> l.isLessThan(JDK_1_9))),
    new Wrapper("java.util.List.of({0})",
                inType -> inType instanceof PsiArrayType && ((PsiArrayType)inType).getComponentType() instanceof PsiClassType,
                outType -> InheritanceUtil.isInheritor(outType, CommonClassNames.JAVA_LANG_ITERABLE) &&
                           isAppropriateLanguageLevel(outType, JavaFeature.COLLECTION_FACTORIES::isSufficient)),
    new Wrapper("java.lang.Math.toIntExact({0})",
                inType -> PsiTypes.longType().equals(inType) || inType.equalsToText(CommonClassNames.JAVA_LANG_LONG),
                outType -> PsiTypes.intType().equals(outType) || outType.equalsToText(CommonClassNames.JAVA_LANG_INTEGER)),
    new Wrapper("java.util.Collections.singleton({0})",
                Predicates.alwaysTrue(),
                outType -> InheritanceUtil.isInheritor(outType, CommonClassNames.JAVA_LANG_ITERABLE)),
    new Wrapper("java.util.Collections.singletonList({0})",
                Predicates.alwaysTrue(),
                outType -> PsiTypesUtil.classNameEquals(outType, CommonClassNames.JAVA_UTIL_LIST)),
    new Wrapper("java.util.Arrays.stream({0})",
                inType -> inType instanceof PsiArrayType,
                outType -> InheritanceUtil.isInheritor(outType, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM))
  };

  private static boolean isAppropriateLanguageLevel(@NotNull PsiType psiType, @NotNull Predicate<? super LanguageLevel> level) {
    if (!(psiType instanceof PsiClassType)) return true;
    return level.test(((PsiClassType)psiType).getLanguageLevel());
  }

  private final @Nullable PsiType myType;
  private final @Nullable AbstractWrapper myWrapper;
  private final @Nullable String myRole;

  public WrapWithAdapterMethodCallFix(@Nullable PsiType type, @NotNull PsiExpression expression, @Nullable String role) {
    this(type, expression, ContainerUtil.find(WRAPPERS, w -> w.isApplicable(expression, expression.getType(), type)), role);
  }

  private WrapWithAdapterMethodCallFix(@Nullable PsiType type,
                                       @NotNull PsiExpression expression,
                                       @Nullable AbstractWrapper wrapper,
                                       @Nullable String role) {
    super(expression);
    myType = type;
    myWrapper = wrapper;
    myRole = role;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpression expression) {
    if (myType == null || myWrapper == null || !myType.isValid() || getModifiedExpression(expression) == null) return null;
    String wrapperText = myWrapper.getText(expression, myType);
    String text = wrapperText == null ? getFamilyName() :
                  myRole == null ?
                  QuickFixBundle.message("wrap.with.adapter.text", wrapperText) :
                  QuickFixBundle.message("wrap.with.adapter.text.role", wrapperText, myRole);
    return Presentation.of(text).withPriority(PriorityAction.Priority.HIGH);
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return QuickFixBundle.message("wrap.with.adapter.call.family.name");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiExpression expression, @NotNull ModPsiUpdater updater) {
    JavaCodeStyleManager.getInstance(context.project()).shortenClassReferences(expression.replace(getModifiedExpression(expression)));
  }

  private PsiExpression getModifiedExpression(@NotNull PsiExpression expression) {
    assert myWrapper != null;
    return myWrapper.getModifiedArgument(expression, myType);
  }

  private static class MyMethodArgumentFix extends MethodArgumentFix {

    MyMethodArgumentFix(@NotNull PsiExpressionList list,
                        int i,
                        @NotNull PsiType toType,
                        @NotNull AbstractWrapper fixerActionFactory) {
      super(list, i, toType, fixerActionFactory);
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpressionList list) {
      Presentation presentation = super.getPresentation(context, list);
      return presentation != null ? presentation.withPriority(PriorityAction.Priority.HIGH) : null;
    }

    @Override
    public @Nls @NotNull String getText(@NotNull PsiExpressionList list) {
      AbstractWrapper wrapper = (AbstractWrapper)myArgumentFixerActionFactory;
      String wrapperText = wrapper.getText(list.getExpressions()[myIndex], myToType);
      if (wrapperText == null) return getFamilyName();
      if (list.getExpressionCount() == 1) {
        return QuickFixBundle.message("wrap.with.adapter.parameter.single.text", wrapperText);
      }
      return QuickFixBundle.message("wrap.with.adapter.parameter.multiple.text", myIndex + 1, wrapperText);
    }
  }

  public static void registerCastActions(CandidateInfo @NotNull [] candidates,
                                         @NotNull PsiCall call,
                                         @NotNull Consumer<? super CommonIntentionAction> info) {
    for (AbstractWrapper wrapper : WRAPPERS) {
      wrapper.registerCastActions(candidates, call, info);
    }
  }
}
