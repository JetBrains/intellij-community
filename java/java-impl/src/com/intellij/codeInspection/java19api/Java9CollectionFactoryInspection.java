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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ConstructionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Tagir Valeev
 */
public class Java9CollectionFactoryInspection extends BaseLocalInspectionTool {
  private static final CallMatcher UNMODIFIABLE_SET =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "unmodifiableSet").parameterCount(1);
  private static final CallMatcher UNMODIFIABLE_LIST =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "unmodifiableList").parameterCount(1);
  private static final CallMatcher ARRAYS_AS_LIST =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_ARRAYS, "asList");
  private static final CallMatcher COLLECTION_ADD =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "add").parameterCount(1);
  private static final CallMatcher STREAM_COLLECT =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "collect").parameterCount(1);
  private static final CallMatcher STREAM_OF =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "of");
  private static final CallMatcher COLLECTORS_TO_SET =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, "toSet").parameterCount(0);
  private static final CallMatcher COLLECTORS_TO_LIST =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, "toList").parameterCount(0);

  private static final CallMapper<PrepopulatedCollectionModel> MAPPER = new CallMapper<PrepopulatedCollectionModel>()
    .register(UNMODIFIABLE_SET, call -> PrepopulatedCollectionModel.fromSet(call.getArgumentList().getExpressions()[0]))
    .register(UNMODIFIABLE_LIST, call -> PrepopulatedCollectionModel.fromList(call.getArgumentList().getExpressions()[0]));

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
        if(model != null) {
          PsiElement element = call.getMethodExpression().getReferenceNameElement();
          if(element != null) {
            holder.registerProblem(element, "Can be replaced with '"+model.myType+".of' call",
                                   new ReplaceWithCollectionFactoryFix(model.myType));
          }
        }
      }
    };
  }

  static class PrepopulatedCollectionModel {
    final List<PsiExpression> myContent;
    final List<PsiElement> myElementsToDelete;
    final String myType;

    PrepopulatedCollectionModel(List<PsiExpression> content, List<PsiElement> delete, String type) {
      myContent = content;
      myElementsToDelete = delete;
      myType = type;
    }

    public static PrepopulatedCollectionModel fromList(PsiExpression listDefinition) {
      listDefinition = PsiUtil.skipParenthesizedExprDown(listDefinition);
      if(listDefinition instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression call = (PsiMethodCallExpression)listDefinition;
        if (ARRAYS_AS_LIST.test(call)) {
          return new PrepopulatedCollectionModel(Arrays.asList(call.getArgumentList().getExpressions()), Collections.emptyList(), "List");
        }
        if(STREAM_COLLECT.test(call) && COLLECTORS_TO_LIST.matches(call.getArgumentList().getExpressions()[0])) {
          PsiMethodCallExpression qualifier = MethodCallUtils.getQualifierMethodCall(call);
          if(STREAM_OF.matches(qualifier)) {
            return new PrepopulatedCollectionModel(Arrays.asList(qualifier.getArgumentList().getExpressions()), Collections.emptyList(),
                                                   "List");
          }
        }
      }
      if(listDefinition instanceof PsiNewExpression) {
        return fromNewExpression((PsiNewExpression)listDefinition, "List", CommonClassNames.JAVA_UTIL_ARRAY_LIST);
      }
      if (listDefinition instanceof PsiReferenceExpression) {
        PsiLocalVariable variable = tryCast(((PsiReferenceExpression)listDefinition).resolve(), PsiLocalVariable.class);
        if (variable != null) {
          return fromVariable(variable, listDefinition, "List", CommonClassNames.JAVA_UTIL_ARRAY_LIST);
        }
      }
      return null;
    }

    public static PrepopulatedCollectionModel fromSet(PsiExpression setDefinition) {
      setDefinition = PsiUtil.skipParenthesizedExprDown(setDefinition);
      if(setDefinition instanceof PsiNewExpression) {
        return fromNewExpression((PsiNewExpression)setDefinition, "Set", CommonClassNames.JAVA_UTIL_HASH_SET);
      }
      if(setDefinition instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression call = (PsiMethodCallExpression)setDefinition;
        if(STREAM_COLLECT.test(call) && COLLECTORS_TO_SET.matches(call.getArgumentList().getExpressions()[0])) {
          PsiMethodCallExpression qualifier = MethodCallUtils.getQualifierMethodCall(call);
          if(STREAM_OF.matches(qualifier)) {
            return new PrepopulatedCollectionModel(Arrays.asList(qualifier.getArgumentList().getExpressions()), Collections.emptyList(),
                                                   "Set");
          }
        }
      }
      if (setDefinition instanceof PsiReferenceExpression) {
        PsiLocalVariable variable = tryCast(((PsiReferenceExpression)setDefinition).resolve(), PsiLocalVariable.class);
        if (variable != null) {
          return fromVariable(variable, setDefinition, "Set", CommonClassNames.JAVA_UTIL_HASH_SET);
        }
      }
      return null;
    }

    @Nullable
    private static PrepopulatedCollectionModel fromVariable(PsiLocalVariable variable,
                                                            PsiExpression expression,
                                                            String typeName, String collectionClass) {
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
          cur = tryCast(PsiTreeUtil.skipSiblingsForward(cur, PsiComment.class, PsiWhiteSpace.class), PsiStatement.class);
          if (PsiTreeUtil.isAncestor(cur, expression, false)) break;
          if (!(cur instanceof PsiExpressionStatement)) return null;
          PsiMethodCallExpression call = tryCast(((PsiExpressionStatement)cur).getExpression(), PsiMethodCallExpression.class);
          if (!COLLECTION_ADD.test(call)) return null;
          if (!refs.remove(call.getMethodExpression().getQualifierExpression())) return null;
          contents.add(call.getArgumentList().getExpressions()[0]);
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
          return fromArraysAsList(args, type);
        }
        PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
        if (anonymousClass != null && args.length == 0) {
          PsiJavaCodeReferenceElement baseClassReference = anonymousClass.getBaseClassReference();
          if (className.equals(baseClassReference.getQualifiedName())) {
            return fromInitializer(anonymousClass, type);
          }
        }
      }
      return null;
    }

    @Nullable
    private static PrepopulatedCollectionModel fromArraysAsList(PsiExpression[] args, String type) {
      if (args.length == 1) {
        PsiMethodCallExpression arg = tryCast(PsiUtil.skipParenthesizedExprDown(args[0]), PsiMethodCallExpression.class);
        if (ARRAYS_AS_LIST.test(arg)) {
          return new PrepopulatedCollectionModel(Arrays.asList(arg.getArgumentList().getExpressions()), Collections.emptyList(), type);
        }
      }
      return null;
    }

    @Nullable
    private static PrepopulatedCollectionModel fromInitializer(PsiAnonymousClass anonymousClass, String type) {
      PsiClassInitializer initializer = ClassUtils.getDoubleBraceInitializer(anonymousClass);
      if(initializer != null) {
        List<PsiExpression> contents = new ArrayList<>();
        for(PsiStatement statement : initializer.getBody().getStatements()) {
          if(!(statement instanceof PsiExpressionStatement)) return null;
          PsiMethodCallExpression call = tryCast(((PsiExpressionStatement)statement).getExpression(), PsiMethodCallExpression.class);
          if(!COLLECTION_ADD.test(call)) return null;
          PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
          if(qualifier != null && !qualifier.getText().equals("this")) return null;
          contents.add(call.getArgumentList().getExpressions()[0]);
        }
        return new PrepopulatedCollectionModel(contents, Collections.emptyList(), type);
      }
      return null;
    }
  }

  private static class ReplaceWithCollectionFactoryFix implements LocalQuickFix {
    private String myType;

    public ReplaceWithCollectionFactoryFix(String type) {myType = type;}

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Replace with '"+myType+".of' call";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with collection factory call";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if(call == null) return;
      PrepopulatedCollectionModel model = MAPPER.mapFirst(call);
      if(model == null) return;
      CommentTracker ct = new CommentTracker();
      model.myElementsToDelete.forEach(ct::delete);
      ct.replaceAndRestoreComments(call, StreamEx.of(model.myContent).map(ct::text)
        .joining(",", "java.util." + model.myType + ".of(", ")"));
    }
  }
}
