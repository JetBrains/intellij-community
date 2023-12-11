// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19api;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.psi.CommonClassNames.*;
import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

public final class Java9CollectionFactoryInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher UNMODIFIABLE_SET = staticCall(JAVA_UTIL_COLLECTIONS, "unmodifiableSet").parameterCount(1);
  private static final CallMatcher UNMODIFIABLE_MAP = staticCall(JAVA_UTIL_COLLECTIONS, "unmodifiableMap").parameterCount(1);
  private static final CallMatcher UNMODIFIABLE_LIST = staticCall(JAVA_UTIL_COLLECTIONS, "unmodifiableList").parameterCount(1);
  private static final CallMatcher ARRAYS_AS_LIST = staticCall(JAVA_UTIL_ARRAYS, "asList");
  private static final CallMatcher COLLECTION_ADD = instanceCall(JAVA_UTIL_COLLECTION, "add").parameterCount(1);
  private static final CallMatcher MAP_PUT = instanceCall(JAVA_UTIL_MAP, "put").parameterCount(2);
  private static final CallMatcher STREAM_COLLECT = instanceCall(JAVA_UTIL_STREAM_STREAM, "collect").parameterCount(1);
  private static final CallMatcher STREAM_OF = staticCall(JAVA_UTIL_STREAM_STREAM, "of");
  private static final CallMatcher COLLECTORS_TO_SET = staticCall(JAVA_UTIL_STREAM_COLLECTORS, "toSet").parameterCount(0);
  private static final CallMatcher COLLECTORS_TO_LIST = staticCall(JAVA_UTIL_STREAM_COLLECTORS, "toList").parameterCount(0);

  private static final CallMapper<PrepopulatedCollectionModel> MAPPER = new CallMapper<PrepopulatedCollectionModel>()
    .register(UNMODIFIABLE_SET, call -> PrepopulatedCollectionModel.fromSet(call.getArgumentList().getExpressions()[0]))
    .register(UNMODIFIABLE_MAP, call -> PrepopulatedCollectionModel.fromMap(call.getArgumentList().getExpressions()[0]))
    .register(UNMODIFIABLE_LIST, call -> PrepopulatedCollectionModel.fromList(call.getArgumentList().getExpressions()[0]));

  public boolean IGNORE_NON_CONSTANT = false;
  public boolean SUGGEST_MAP_OF_ENTRIES = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("IGNORE_NON_CONSTANT", JavaBundle.message("inspection.collection.factories.option.ignore.non.constant")),
      checkbox("SUGGEST_MAP_OF_ENTRIES", JavaBundle.message("inspection.collection.factories.option.suggest.ofentries")));
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel9OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        PrepopulatedCollectionModel model = MAPPER.mapFirst(call);
        if (model != null && model.isValid(SUGGEST_MAP_OF_ENTRIES)) {
          ProblemHighlightType type = model.myConstantContent || !IGNORE_NON_CONSTANT
                                      ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                                      : ProblemHighlightType.INFORMATION;
          if (type == ProblemHighlightType.INFORMATION && !isOnTheFly) return;
          boolean wholeStatement = isOnTheFly &&
                                   (type == ProblemHighlightType.INFORMATION ||
                                    InspectionProjectProfileManager.isInformationLevel(getShortName(), call));
          PsiElement element = wholeStatement ? call : call.getMethodExpression().getReferenceNameElement();
          if(element != null) {
            String replacementMethod = model.hasTooManyMapEntries() ? "ofEntries" : model.myCopy ? "copyOf" : "of";
            String fixMessage = JavaBundle.message("inspection.collection.factories.fix.name", model.myType, replacementMethod);
            String inspectionMessage =
              JavaBundle.message("inspection.collection.factories.message", model.myType, replacementMethod);
            holder.registerProblem(element, inspectionMessage, type, new ReplaceWithCollectionFactoryFix(fixMessage));
          }
        }
      }
    };
  }

  static class PrepopulatedCollectionModel {
    final List<PsiExpression> myContent;
    final List<PsiElement> myElementsToDelete;
    final String myType;
    final boolean myCopy;
    final boolean myConstantContent;
    final boolean myRepeatingKeys;
    final boolean myMayHaveNulls;

    PrepopulatedCollectionModel(List<PsiExpression> content, List<PsiElement> delete, String type) {
      this(content, delete, type, false);
    }

    PrepopulatedCollectionModel(List<PsiExpression> content, List<PsiElement> delete, String type, boolean copy) {
      myContent = content;
      myElementsToDelete = delete;
      myType = type;
      myCopy = copy;
      Map<PsiExpression, List<Object>> constants = StreamEx.of(myContent)
        .cross(ExpressionUtils::nonStructuralChildren).mapValues(ExpressionUtils::computeConstantExpression).distinct().grouping();
      myConstantContent = !copy && StreamEx.ofValues(constants).flatCollection(Function.identity()).allMatch(Objects::nonNull);
      myRepeatingKeys = keyExpressions().flatCollection(constants::get).nonNull().distinct(2).findAny().isPresent();
      myMayHaveNulls = !copy && StreamEx.of(myContent).flatMap(ExpressionUtils::nonStructuralChildren)
        .anyMatch(ex -> NullabilityUtil.getExpressionNullability(ex, true) != Nullability.NOT_NULL);
    }

    boolean isValid(boolean suggestMapOfEntries) {
      return !myMayHaveNulls && !myRepeatingKeys && (suggestMapOfEntries || !hasTooManyMapEntries());
    }

    private boolean hasTooManyMapEntries() {
      return myType.equals("Map") && myContent.size() > 20;
    }

    private StreamEx<PsiExpression> keyExpressions() {
      return switch (myType) {
        case "Set" -> StreamEx.of(myContent);
        case "Map" -> IntStreamEx.range(0, myContent.size(), 2).elements(myContent);
        default -> StreamEx.empty();
      };
    }

    public static PrepopulatedCollectionModel fromList(PsiExpression listDefinition) {
      listDefinition = PsiUtil.skipParenthesizedExprDown(listDefinition);
      if(listDefinition instanceof PsiMethodCallExpression call) {
        if (ARRAYS_AS_LIST.test(call)) {
          return new PrepopulatedCollectionModel(Arrays.asList(call.getArgumentList().getExpressions()), Collections.emptyList(), "List");
        }
        return fromCollect(call, "List", COLLECTORS_TO_LIST);
      }
      if(listDefinition instanceof PsiNewExpression newExpression) {
        return fromNewExpression(newExpression, "List", JAVA_UTIL_ARRAY_LIST);
      }
      if (listDefinition instanceof PsiReferenceExpression ref) {
        return fromVariable(ref, "List", JAVA_UTIL_ARRAY_LIST, COLLECTION_ADD);
      }
      return null;
    }

    public static PrepopulatedCollectionModel fromSet(PsiExpression setDefinition) {
      setDefinition = PsiUtil.skipParenthesizedExprDown(setDefinition);
      if (setDefinition instanceof PsiMethodCallExpression call) {
        return fromCollect(call, "Set", COLLECTORS_TO_SET);
      }
      if (setDefinition instanceof PsiNewExpression newExpression) {
        return fromNewExpression(newExpression, "Set", JAVA_UTIL_HASH_SET);
      }
      if (setDefinition instanceof PsiReferenceExpression ref) {
        return fromVariable(ref, "Set", JAVA_UTIL_HASH_SET, COLLECTION_ADD);
      }
      return null;
    }

    public static PrepopulatedCollectionModel fromMap(PsiExpression mapDefinition) {
      mapDefinition = PsiUtil.skipParenthesizedExprDown(mapDefinition);
      if (mapDefinition instanceof PsiReferenceExpression ref) {
        return fromVariable(ref, "Map", JAVA_UTIL_HASH_MAP, MAP_PUT);
      }
      if (mapDefinition instanceof PsiNewExpression newExpression) {
        PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
        PsiExpressionList argumentList = newExpression.getArgumentList();
        if (argumentList != null) {
          PsiExpression[] args = argumentList.getExpressions();
          PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
          if (classReference != null && PsiUtil.isLanguageLevel10OrHigher(mapDefinition) &&
              JAVA_UTIL_HASH_MAP.equals(classReference.getQualifiedName()) && args.length == 1) {
            PsiExpression arg = PsiUtil.skipParenthesizedExprDown(args[0]);
            if (arg != null) {
              PsiType sourceType = arg.getType();
              PsiType targetType = newExpression.getType();
              if (targetType != null && sourceType != null && sourceType.isAssignableFrom(targetType)) {
                return new PrepopulatedCollectionModel(Collections.singletonList(arg), Collections.emptyList(), "Map", true);
              }
            }
          }
          if (anonymousClass != null && argumentList.isEmpty()) {
            PsiJavaCodeReferenceElement baseClassReference = anonymousClass.getBaseClassReference();
            if (JAVA_UTIL_HASH_MAP.equals(baseClassReference.getQualifiedName())) {
              return fromInitializer(anonymousClass, "Map", MAP_PUT);
            }
          }
        }
      }
      return null;
    }

    @Nullable
    private static PrepopulatedCollectionModel fromCollect(PsiMethodCallExpression call, String typeName, CallMatcher collector) {
      if (STREAM_COLLECT.test(call) && collector.matches(call.getArgumentList().getExpressions()[0])) {
        PsiMethodCallExpression qualifier = MethodCallUtils.getQualifierMethodCall(call);
        if (STREAM_OF.matches(qualifier)) {
          return new PrepopulatedCollectionModel(Arrays.asList(qualifier.getArgumentList().getExpressions()), Collections.emptyList(),
                                                 typeName);
        }
      }
      return null;
    }

    @Nullable
    private static PrepopulatedCollectionModel fromVariable(PsiReferenceExpression expression,
                                                            String typeName, String collectionClass, CallMatcher addMethod) {
      PsiLocalVariable variable = tryCast(expression.resolve(), PsiLocalVariable.class);
      if (variable == null) return null;
      PsiCodeBlock block = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      PsiDeclarationStatement declaration = PsiTreeUtil.getParentOfType(variable, PsiDeclarationStatement.class);
      if (block == null || declaration == null) return null;
      PsiElement[] defs = DefUseUtil.getDefs(block, variable, expression);
      if (defs.length == 1 && defs[0] == variable) {
        PsiExpression initializer = variable.getInitializer();
        if (!ConstructionUtils.isEmptyCollectionInitializer(initializer)) return null;
        if (!PsiTypesUtil.classNameEquals(initializer.getType(), collectionClass)) return null;
        Set<PsiElement> refs = ContainerUtil.newHashSet(DefUseUtil.getRefs(block, variable, initializer));
        refs.remove(expression);
        PsiStatement cur = declaration;
        List<PsiExpression> contents = new ArrayList<>();
        List<PsiElement> elementsToRemove = new ArrayList<>();
        elementsToRemove.add(initializer);
        while (true) {
          cur = tryCast(PsiTreeUtil.skipWhitespacesAndCommentsForward(cur), PsiStatement.class);
          if (PsiTreeUtil.isAncestor(cur, expression, false)) break;
          if (!(cur instanceof PsiExpressionStatement)) return null;
          PsiMethodCallExpression call = tryCast(((PsiExpressionStatement)cur).getExpression(), PsiMethodCallExpression.class);
          if (!addMethod.test(call)) return null;
          if (!refs.remove(call.getMethodExpression().getQualifierExpression())) return null;
          contents.addAll(Arrays.asList(call.getArgumentList().getExpressions()));
          elementsToRemove.add(cur);
        }
        if (!refs.isEmpty()) return null;
        return new PrepopulatedCollectionModel(contents, elementsToRemove, typeName);
      }
      return null;
    }

    @Nullable
    private static PrepopulatedCollectionModel fromNewExpression(PsiNewExpression newExpression, String type, String className) {
      PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList != null) {
        PsiExpression[] args = argumentList.getExpressions();
        PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
        if (classReference != null && className.equals(classReference.getQualifiedName())) {
          return fromCopyConstructor(args, type);
        }
        PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
        if (anonymousClass != null && args.length == 0) {
          PsiJavaCodeReferenceElement baseClassReference = anonymousClass.getBaseClassReference();
          if (className.equals(baseClassReference.getQualifiedName())) {
            return fromInitializer(anonymousClass, type, COLLECTION_ADD);
          }
        }
      }
      return null;
    }

    @Nullable
    private static PrepopulatedCollectionModel fromCopyConstructor(PsiExpression[] args, String type) {
      if (args.length == 1) {
        PsiExpression arg = PsiUtil.skipParenthesizedExprDown(args[0]);
        PsiMethodCallExpression call = tryCast(arg, PsiMethodCallExpression.class);
        if (ARRAYS_AS_LIST.test(call)) {
          return new PrepopulatedCollectionModel(Arrays.asList(call.getArgumentList().getExpressions()), Collections.emptyList(), type);
        }
        if(arg != null && PsiUtil.isLanguageLevel10OrHigher(arg) && InheritanceUtil.isInheritor(arg.getType(), JAVA_UTIL_COLLECTION)) {
          return new PrepopulatedCollectionModel(Collections.singletonList(arg), Collections.emptyList(), type, true);
        }
      }
      return null;
    }

    @Nullable
    private static PrepopulatedCollectionModel fromInitializer(PsiAnonymousClass anonymousClass, String type, CallMatcher addMethod) {
      PsiClassInitializer initializer = ClassUtils.getDoubleBraceInitializer(anonymousClass);
      if(initializer != null) {
        List<PsiExpression> contents = new ArrayList<>();
        for(PsiStatement statement : initializer.getBody().getStatements()) {
          if(!(statement instanceof PsiExpressionStatement)) return null;
          PsiMethodCallExpression call = tryCast(((PsiExpressionStatement)statement).getExpression(), PsiMethodCallExpression.class);
          if(!addMethod.test(call)) return null;
          PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
          if(qualifier != null && !qualifier.getText().equals("this")) return null;
          contents.addAll(Arrays.asList(call.getArgumentList().getExpressions()));
        }
        return new PrepopulatedCollectionModel(contents, Collections.emptyList(), type);
      }
      return null;
    }
  }

  private static class ReplaceWithCollectionFactoryFix extends PsiUpdateModCommandQuickFix {
    private final @IntentionName String myMessage;

    ReplaceWithCollectionFactoryFix(@IntentionName String message) {
      myMessage = message;
    }

    @NotNull
    @Override
    public String getName() {
      return myMessage;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.collection.factories.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, false);
      if(call == null) return;
      PrepopulatedCollectionModel model = MAPPER.mapFirst(call);
      if(model == null) return;
      String typeArgument = getTypeArguments(call.getType(), model.myType);
      CommentTracker ct = new CommentTracker();
      String replacementText;
      if (model.myCopy) {
        assert model.myContent.size() == 1;
        replacementText = "java.util." + model.myType + "." + typeArgument + "copyOf(" + model.myContent.get(0).getText() + ")";
      }
      else if (model.hasTooManyMapEntries()) {
        replacementText = StreamEx.ofSubLists(model.myContent, 2)
          .map(expr -> ct.commentsBefore(expr.get(0)) +
                       "java.util.Map.entry(" + ct.text(expr.get(0)) + "," + ct.textWithComments(expr.get(1)) + ")")
          .joining(",", "java.util.Map." + typeArgument + "ofEntries(", ")");
      }
      else {
        replacementText = StreamEx.of(model.myContent)
          .map(ct::textWithComments)
          .joining(",", "java.util." + model.myType + "." + typeArgument + "of(", ")");
      }
      List<PsiLocalVariable> vars =
        StreamEx.of(model.myElementsToDelete).map(PsiElement::getParent).select(PsiLocalVariable.class).toList();
      model.myElementsToDelete.forEach(ct::delete);
      PsiElement replacement = ct.replaceAndRestoreComments(call, replacementText);
      vars.stream().filter(var -> !VariableAccessUtils.variableIsUsed(var, PsiUtil.getVariableCodeBlock(var, null)))
        .forEach(PsiElement::delete);
      RemoveRedundantTypeArgumentsUtil.removeRedundantTypeArguments(replacement);
    }

    @NotNull
    private static String getTypeArguments(PsiType type, String typeName) {
      if (typeName.equals("Map")) {
        PsiType keyType = PsiUtil.substituteTypeParameter(type, JAVA_UTIL_MAP, 0, false);
        PsiType valueType = PsiUtil.substituteTypeParameter(type, JAVA_UTIL_MAP, 1, false);
        if (keyType != null && valueType != null) {
          return "<" + keyType.getCanonicalText() + "," + valueType.getCanonicalText() + ">";
        }
      }
      else {
        PsiType elementType = PsiUtil.substituteTypeParameter(type, JAVA_UTIL_COLLECTION, 0, false);
        if (elementType != null) {
          return "<" + elementType.getCanonicalText() + ">";
        }
      }
      return "";
    }
  }
}
