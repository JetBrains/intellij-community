/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.java19api;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.function.Function;

import static com.intellij.psi.CommonClassNames.*;
import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

public class Java9CollectionFactoryInspection extends BaseLocalInspectionTool {
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

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionsBundle.message("inspection.collection.factories.option.ignore.non.constant"), "IGNORE_NON_CONSTANT");
    panel.addCheckbox(InspectionsBundle.message("inspection.collection.factories.option.suggest.ofentries"), "SUGGEST_MAP_OF_ENTRIES");
    return panel;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel9OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
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
            String fixMessage = InspectionsBundle.message("inspection.collection.factories.fix.name", model.myType, replacementMethod);
            String inspectionMessage =
              InspectionsBundle.message("inspection.collection.factories.message", model.myType, replacementMethod);
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
    final boolean myHasNulls;

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
      myHasNulls = StreamEx.of(myContent).flatMap(ExpressionUtils::nonStructuralChildren).map(PsiExpression::getType).has(PsiType.NULL);
    }

    boolean isValid(boolean suggestMapOfEntries) {
      return !myHasNulls && !myRepeatingKeys && (suggestMapOfEntries || !hasTooManyMapEntries());
    }

    private boolean hasTooManyMapEntries() {
      return myType.equals("Map") && myContent.size() > 20;
    }

    private StreamEx<PsiExpression> keyExpressions() {
      switch (myType) {
        case "Set":
          return StreamEx.of(myContent);
        case "Map":
          return IntStreamEx.range(0, myContent.size(), 2).elements(myContent);
        default:
          return StreamEx.empty();
      }
    }

    public static PrepopulatedCollectionModel fromList(PsiExpression listDefinition) {
      listDefinition = PsiUtil.skipParenthesizedExprDown(listDefinition);
      if(listDefinition instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression call = (PsiMethodCallExpression)listDefinition;
        if (ARRAYS_AS_LIST.test(call)) {
          return new PrepopulatedCollectionModel(Arrays.asList(call.getArgumentList().getExpressions()), Collections.emptyList(), "List");
        }
        return fromCollect(call, "List", COLLECTORS_TO_LIST);
      }
      if(listDefinition instanceof PsiNewExpression) {
        return fromNewExpression((PsiNewExpression)listDefinition, "List", JAVA_UTIL_ARRAY_LIST);
      }
      if (listDefinition instanceof PsiReferenceExpression) {
        return fromVariable((PsiReferenceExpression)listDefinition, "List", JAVA_UTIL_ARRAY_LIST, COLLECTION_ADD);
      }
      return null;
    }

    public static PrepopulatedCollectionModel fromSet(PsiExpression setDefinition) {
      setDefinition = PsiUtil.skipParenthesizedExprDown(setDefinition);
      if (setDefinition instanceof PsiMethodCallExpression) {
        return fromCollect((PsiMethodCallExpression)setDefinition, "Set", COLLECTORS_TO_SET);
      }
      if (setDefinition instanceof PsiNewExpression) {
        return fromNewExpression((PsiNewExpression)setDefinition, "Set", JAVA_UTIL_HASH_SET);
      }
      if (setDefinition instanceof PsiReferenceExpression) {
        return fromVariable((PsiReferenceExpression)setDefinition, "Set", JAVA_UTIL_HASH_SET, COLLECTION_ADD);
      }
      return null;
    }

    public static PrepopulatedCollectionModel fromMap(PsiExpression mapDefinition) {
      mapDefinition = PsiUtil.skipParenthesizedExprDown(mapDefinition);
      if (mapDefinition instanceof PsiReferenceExpression) {
        return fromVariable((PsiReferenceExpression)mapDefinition, "Map", JAVA_UTIL_HASH_MAP, MAP_PUT);
      }
      if (mapDefinition instanceof PsiNewExpression) {
        PsiNewExpression newExpression = (PsiNewExpression)mapDefinition;
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
        PsiClassType type = tryCast(initializer.getType(), PsiClassType.class);
        if (type == null || !type.rawType().equalsToText(collectionClass)) return null;
        Set<PsiElement> refs = ContainerUtil.set(DefUseUtil.getRefs(block, variable, initializer));
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
          return fromCopyConstructor(newExpression, args, type);
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
    private static PrepopulatedCollectionModel fromCopyConstructor(PsiNewExpression newExpression,
                                                                   PsiExpression[] args,
                                                                   String type) {
      if (args.length == 1) {
        PsiExpression arg = PsiUtil.skipParenthesizedExprDown(args[0]);
        PsiMethodCallExpression call = tryCast(arg, PsiMethodCallExpression.class);
        if (ARRAYS_AS_LIST.test(call)) {
          return new PrepopulatedCollectionModel(Arrays.asList(call.getArgumentList().getExpressions()), Collections.emptyList(), type);
        }
        if(arg != null && PsiUtil.isLanguageLevel10OrHigher(arg)) {
          PsiType sourceType = arg.getType();
          PsiType targetType = newExpression.getType();
          if (targetType != null && sourceType != null && sourceType.isAssignableFrom(targetType)) {
            return new PrepopulatedCollectionModel(Collections.singletonList(arg), Collections.emptyList(), type, true);
          }
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

  private static class ReplaceWithCollectionFactoryFix implements LocalQuickFix {
    private final String myMessage;

    public ReplaceWithCollectionFactoryFix(String message) {
      myMessage = message;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myMessage;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.collection.factories.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class, false);
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
          .prepend(Collections.<PsiExpression>emptyList())
          .pairMap((prev, next) -> {
            String prevComment = prev.isEmpty() ? "" : CommentTracker.commentsBetween(prev.get(1), next.get(0));
            String midComment = CommentTracker.commentsBetween(next.get(0), next.get(1));
            return prevComment + "java.util.Map.entry(" + ct.text(next.get(0)) + "," + midComment + ct.text(next.get(1)) + ")";
          })
          .joining(",", "java.util.Map." + typeArgument + "ofEntries(", ")");
      }
      else {
        replacementText = StreamEx.of(model.myContent)
          .prepend((PsiExpression)null)
          .pairMap((prev, next) -> (prev == null ? "" : CommentTracker.commentsBetween(prev, next)) + ct.text(next))
          .joining(",", "java.util." + model.myType + "." + typeArgument + "of(", ")");
      }
      List<PsiLocalVariable> vars =
        StreamEx.of(model.myElementsToDelete).map(PsiElement::getParent).select(PsiLocalVariable.class).toList();
      model.myElementsToDelete.forEach(ct::delete);
      PsiElement replacement = ct.replaceAndRestoreComments(call, replacementText);
      vars.stream().filter(var -> ReferencesSearch.search(var).findFirst() == null).forEach(PsiElement::delete);
      PsiDiamondTypeUtil.removeRedundantTypeArguments(replacement);
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
