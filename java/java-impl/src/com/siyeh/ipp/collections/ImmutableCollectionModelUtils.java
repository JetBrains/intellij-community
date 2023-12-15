// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.collections;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.psi.CommonClassNames.*;
import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

final class ImmutableCollectionModelUtils {

  private static final CallMatcher MAP_ENTRY_CALL = staticCall(JAVA_UTIL_MAP, "entry").parameterCount(2);

  @Nullable
  static ImmutableCollectionModel createModel(@NotNull PsiMethodCallExpression call) {
    CollectionType type = CollectionType.create(call);
    if (type == null) return null;
    if (!CodeBlockSurrounder.canSurround(call)) return null;
    String assignedVariable = getAssignedVariable(call);
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    PsiClassType classType = ObjectUtils.tryCast(call.getType(), PsiClassType.class);
    if (classType == null) return null;
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(call.getProject()).getResolveHelper();
    boolean hasNonResolvedTypeParams = Arrays.stream(classType.getParameters())
      .map(PsiUtil::resolveClassInClassTypeOnly)
      .anyMatch(aClass -> isNonResolvedTypeParameter(aClass, call, resolveHelper));
    if (hasNonResolvedTypeParams) return null;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if ("ofEntries".equals(method.getName()) && ContainerUtil.exists(args, arg -> extractPutArgs(arg) == null)) return null;
    return new ImmutableCollectionModel(call, type, method, assignedVariable);
  }

  @Contract("null, _, _ -> false")
  private static boolean isNonResolvedTypeParameter(@Nullable PsiClass parameter,
                                                    @NotNull PsiElement context,
                                                    @NotNull PsiResolveHelper resolveHelper) {
    if (!(parameter instanceof PsiTypeParameter typeParameter)) return false;
    String name = typeParameter.getName();
    return name == null || resolveHelper.resolveReferencedClass(name, context) != parameter;
  }

  static void replaceWithMutable(@NotNull ImmutableCollectionModel model, @NotNull ModPsiUpdater updater) {
    ToMutableCollectionConverter.convert(model, updater);
  }

  @Nullable
  private static String getAssignedVariable(@NotNull PsiMethodCallExpression call) {
    PsiElement parent = PsiTreeUtil.getParentOfType(call, PsiVariable.class, PsiAssignmentExpression.class);
    if (parent == null) return null;
    if (parent instanceof PsiVariable) {
      PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(((PsiVariable)parent).getInitializer());
      return initializer == call ? ((PsiVariable)parent).getName() : null;
    }
    PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
    PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(assignment.getRExpression());
    if (rhs != call) return null;
    PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignment.getLExpression());
    PsiReferenceExpression ref = ObjectUtils.tryCast(lhs, PsiReferenceExpression.class);
    if (ref == null) return null;
    PsiExpression qualifier = ref.getQualifierExpression();
    if (qualifier != null && SideEffectChecker.mayHaveSideEffects(qualifier)) return null;
    PsiVariable variable = ObjectUtils.tryCast(ref.resolve(), PsiVariable.class);
    if (variable == null || variable instanceof PsiField && variable.hasModifierProperty(PsiModifier.VOLATILE)) return null;
    return ref.getText();
  }

  @Nullable
  private static String extractPutArgs(@NotNull PsiExpression entryExpression) {
    if (entryExpression instanceof PsiReferenceExpression) {
      return MessageFormat.format("{0}.getKey(), {0}.getValue()", entryExpression.getText());
    }
    PsiCallExpression call = ObjectUtils.tryCast(entryExpression, PsiCallExpression.class);
    if (call == null || !isEntryConstruction(call)) return null;
    PsiExpressionList argumentList = call.getArgumentList();
    if (argumentList == null) return null;
    PsiExpression[] expressions = argumentList.getExpressions();
    if (expressions.length == 1) return extractPutArgs(expressions[0]);
    if (expressions.length == 2) return expressions[0].getText() + "," + expressions[1].getText();
    return null;
  }

  private static boolean isEntryConstruction(@NotNull PsiCallExpression call) {
    if (MAP_ENTRY_CALL.matches(call)) return true;
    PsiNewExpression newExpression = ObjectUtils.tryCast(call, PsiNewExpression.class);
    return newExpression != null && InheritanceUtil.isInheritor(newExpression.getType(), JAVA_UTIL_MAP_ENTRY);
  }

  private enum CollectionType {
    MAP(JAVA_UTIL_HASH_MAP), LIST(JAVA_UTIL_ARRAY_LIST), SET(JAVA_UTIL_HASH_SET);

    private final String myMutableClass;

    private static final CallMapper<CollectionType> MAPPER = new CallMapper<CollectionType>()
      .register(anyOf(
        staticCall(JAVA_UTIL_COLLECTIONS, "emptyMap").parameterCount(0),
        staticCall(JAVA_UTIL_COLLECTIONS, "singletonMap").parameterCount(2),
        staticCall(JAVA_UTIL_MAP, "of"),
        staticCall(JAVA_UTIL_MAP, "ofEntries"),
        staticCall("com.google.common.collect.ImmutableMap", "of")), MAP)
      .register(anyOf(
        staticCall(JAVA_UTIL_COLLECTIONS, "emptyList").parameterCount(0),
        staticCall(JAVA_UTIL_COLLECTIONS, "singletonList").parameterCount(1),
        staticCall(JAVA_UTIL_LIST, "of"),
        staticCall("com.google.common.collect.ImmutableList", "of")), LIST)
      .register(anyOf(
        staticCall(JAVA_UTIL_COLLECTIONS, "emptySet").parameterCount(0),
        staticCall(JAVA_UTIL_COLLECTIONS, "singleton").parameterCount(1),
        staticCall(JAVA_UTIL_SET, "of"),
        staticCall("com.google.common.collect.ImmutableSet", "of")), SET);

    CollectionType(String className) {
      myMutableClass = className;
    }

    @NotNull
    String getInitializerText(@Nullable String copyFrom) {
      return String.format("new " + myMutableClass + "<>(%s)", StringUtil.notNullize(copyFrom));
    }

    @Nullable
    static CollectionType create(@NotNull PsiMethodCallExpression call) {
      CollectionType type = MAPPER.mapFirst(call);
      if (type == null) return null;
      PsiType expectedType = ExpectedTypeUtils.findExpectedType(call, false);
      if (expectedType == null) return null;
      PsiClassType newType = TypeUtils.getType(type.myMutableClass, call);
      return expectedType.isAssignableFrom(newType) ? type : null;
    }
  }

  /**
   * Replaces immutable collection creation with mutable one.
   */
  private static final class ToMutableCollectionConverter {
    private final PsiElementFactory myElementFactory;
    private final JavaCodeStyleManager myCodeStyleManager;
    private final @NotNull ModPsiUpdater myUpdater;

    private ToMutableCollectionConverter(@NotNull Project project, @NotNull ModPsiUpdater updater) {
      myElementFactory = PsiElementFactory.getInstance(project);
      myCodeStyleManager = JavaCodeStyleManager.getInstance(project);
      myUpdater = updater;
    }

    private void replaceWithMutable(@NotNull ImmutableCollectionModel model) {
      CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(model.myCall);
      if (surrounder == null) return;
      CodeBlockSurrounder.SurroundResult result = surrounder.surround();
      PsiMethodCallExpression call = (PsiMethodCallExpression)result.getExpression();
      PsiStatement statement = result.getAnchor();

      model.myCall = call;
      String assignedVariable = model.myAssignedVariable;
      if (assignedVariable != null) {
        String initializerText = model.myType.getInitializerText(model.myIsVarArgCall ? null : call.getText());
        PsiElement anchor = addUpdates(assignedVariable, model, statement);
        PsiReplacementUtil.replaceExpressionAndShorten(call, initializerText, new CommentTracker());
        myUpdater.moveCaretTo(anchor.getTextRange().getEndOffset());
      }
      else {
        createVariable(statement, model);
      }
    }

    private void createVariable(@NotNull PsiStatement statement, @NotNull ImmutableCollectionModel model) {
      PsiMethodCallExpression call = model.myCall;
      PsiType type = call.getType();
      if (type == null) return;
      String[] names = getNameSuggestions(call, type);
      if (names.length == 0) return;
      String name = names[0];
      PsiDeclarationStatement declaration = createDeclaration(name, type, model, statement);
      if (declaration == null) return;
      PsiVariable declaredVariable = (PsiVariable)declaration.getDeclaredElements()[0];
      addUpdates(name, model, declaration);
      if (call.getParent() instanceof PsiExpressionStatement) {
        new CommentTracker().deleteAndRestoreComments(statement);
      }
      else {
        PsiReplacementUtil.replaceExpression(call, name, new CommentTracker());
      }
      myUpdater.rename(declaredVariable, List.of(names));
    }

    @Nullable
    private PsiDeclarationStatement createDeclaration(@NotNull String name,
                                                      @NotNull PsiType type,
                                                      @NotNull ImmutableCollectionModel model,
                                                      @NotNull PsiStatement usage) {
      String initializerText = model.myType.getInitializerText(model.myIsVarArgCall ? null : model.myCall.getText());
      PsiExpression initializer = myElementFactory.createExpressionFromText(initializerText, null);
      PsiType rhsType = initializer.getType();
      if (rhsType == null) return null;
      if (!TypeUtils.areConvertible(type, rhsType)) {
        type = ExpectedTypeUtils.findExpectedType(model.myCall, false);
      }
      if (type == null) return null;
      PsiDeclarationStatement declaration = myElementFactory.createVariableDeclarationStatement(name, type, initializer);
      return ObjectUtils.tryCast(BlockUtils.addBefore(usage, declaration), PsiDeclarationStatement.class);
    }

    private String @NotNull [] getNameSuggestions(@NotNull PsiMethodCallExpression call, @NotNull PsiType type) {
      String propertyName = getPropertyName(call, type);
      SuggestedNameInfo nameInfo = myCodeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, propertyName, call, type);
      return myCodeStyleManager.suggestUniqueVariableName(nameInfo, call, true).names;
    }

    @NotNull
    private String getPropertyName(@NotNull PsiMethodCallExpression call, @NotNull PsiType type) {
      String propertyName = getPropertyNameByCall(call);
      if (propertyName != null) return propertyName;
      return myCodeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, type).names[0];
    }

    @NotNull
    private PsiStatement addUpdates(@NotNull String variable, @NotNull ImmutableCollectionModel model, @NotNull PsiStatement anchor) {
      if (!model.myIsVarArgCall) return anchor;
      return StreamEx.of(createUpdates(variable, model))
        .map(update -> myElementFactory.createStatementFromText(update, null))
        .foldLeft(anchor, (acc, update) -> BlockUtils.addAfter(acc, update));
    }

    @NotNull
    private static List<String> createUpdates(@NotNull String name, @NotNull ImmutableCollectionModel model) {
      boolean isMapOfEntriesCall = "ofEntries".equals(model.myCall.getMethodExpression().getReferenceName());
      List<String> updates = new ArrayList<>();
      PsiExpression[] args = model.myCall.getArgumentList().getExpressions();
      for (int i = 0; i < args.length; i++) {
        PsiExpression arg = args[i];
        if (model.myType != CollectionType.MAP) {
          updates.add(String.format("%s.add(%s);", name, arg.getText()));
        }
        else if (isMapOfEntriesCall) {
          updates.add(String.format("%s.put(%s);", name, extractPutArgs(arg)));
        }
        else if (i % 2 != 0) {
          updates.add(String.format("%s.put(%s, %s);", name, args[i - 1].getText(), arg.getText()));
        }
      }
      return updates;
    }

    @Nullable
    private static String getPropertyNameByCall(@NotNull PsiMethodCallExpression call) {
      PsiMethodCallExpression outerCall = PsiTreeUtil.getParentOfType(call, PsiMethodCallExpression.class);
      if (outerCall == null) return null;
      PsiMethod method = outerCall.resolveMethod();
      if (method == null) return null;
      PsiExpression[] arguments = outerCall.getArgumentList().getExpressions();
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length == 0) return null;
      for (int i = 0; i < arguments.length; i++) {
        if (arguments[i] == call) {
          int idx = i >= parameters.length ? parameters.length - 1 : i;
          return parameters[idx].getName();
        }
      }
      return null;
    }

    static void convert(@NotNull ImmutableCollectionModel model, @NotNull ModPsiUpdater updater) {
      new ToMutableCollectionConverter(model.myCall.getProject(), updater).replaceWithMutable(model);
    }
  }

  /**
   * Represents immutable collection creation call (e.g. {@link Collections#singleton(Object)}).
   */
  static class ImmutableCollectionModel {

    private PsiMethodCallExpression myCall;
    private final CollectionType myType;
    private final boolean myIsVarArgCall;
    private final String myAssignedVariable;

    @Contract(pure = true)
    ImmutableCollectionModel(@NotNull PsiMethodCallExpression call,
                             @NotNull CollectionType type,
                             @NotNull PsiMethod method,
                             @Nullable String assignedVariable) {
      myCall = call;
      myType = type;
      myIsVarArgCall = !method.isVarArgs() || MethodCallUtils.isVarArgCall(call);
      myAssignedVariable = assignedVariable;
    }

    public String getText() {
      return switch (myType) {
        case MAP -> "new HashMap<>()";
        case LIST -> "new ArrayList<>()";
        case SET -> "new HashSet<>()";
      };
    }
  }
}
