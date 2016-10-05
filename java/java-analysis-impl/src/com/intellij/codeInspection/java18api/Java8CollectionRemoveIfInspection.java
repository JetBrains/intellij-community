/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.java18api;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Tagir Valeev
 */
public class Java8CollectionRemoveIfInspection extends BaseJavaBatchLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      void handleIteratorLoop(PsiLoopStatement statement, PsiJavaToken endToken, IteratorDeclaration declaration) {
        if (endToken == null) return;
        PsiStatement body = statement.getBody();
        if(!(body instanceof PsiBlockStatement)) return;
        PsiStatement[] statements = ((PsiBlockStatement)body).getCodeBlock().getStatements();
        if (statements.length == 2 && statements[1] instanceof PsiIfStatement) {
          PsiVariable element = declaration.getNextElementVariable(statements[0]);
          if (element == null) return;
          PsiIfStatement ifStatement = (PsiIfStatement)statements[1];
          if(checkAndExtractCondition(declaration, ifStatement) == null) return;
          registerProblem(statement, endToken);
        }
        else if (statements.length == 1 && statements[0] instanceof PsiIfStatement){
          PsiIfStatement ifStatement = (PsiIfStatement)statements[0];
          PsiExpression condition = checkAndExtractCondition(declaration, ifStatement);
          if (condition == null) return;
          PsiElement ref = declaration.findOnlyIteratorRef(condition);
          if (ref != null && declaration.isIteratorMethodCall(ref.getParent().getParent(), "next") && isAlwaysExecuted(condition, ref)) {
            registerProblem(statement, endToken);
          }
        }
      }

      private boolean isAlwaysExecuted(PsiExpression condition, PsiElement ref) {
        while(ref != condition) {
          PsiElement parent = ref.getParent();
          if(parent instanceof PsiPolyadicExpression) {
            PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
            IElementType type = polyadicExpression.getOperationTokenType();
            if ((type.equals(JavaTokenType.ANDAND) || type.equals(JavaTokenType.OROR)) && polyadicExpression.getOperands()[0] != ref) {
              return false;
            }
          }
          if(parent instanceof PsiConditionalExpression && ((PsiConditionalExpression)parent).getCondition() != ref) {
            return false;
          }
          ref = parent;
        }
        return true;
      }

      private void registerProblem(PsiLoopStatement statement, PsiJavaToken endToken) {
        //noinspection DialogTitleCapitalization
        holder.registerProblem(statement, new TextRange(0, endToken.getTextOffset() - statement.getTextOffset() + 1),
                               QuickFixBundle.message("java.8.collection.removeif.inspection.description"),
                               new ReplaceWithRemoveIfQuickFix());
      }

      @Nullable
      private PsiExpression checkAndExtractCondition(IteratorDeclaration declaration,
                                                     PsiIfStatement ifStatement) {
        PsiExpression condition = ifStatement.getCondition();
        if (condition == null || ifStatement.getElseBranch() != null) return null;
        PsiStatement thenStatement = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
        if (!(thenStatement instanceof PsiExpressionStatement)) return null;
        if (!declaration.isIteratorMethodCall(((PsiExpressionStatement)thenStatement).getExpression(), "remove")) return null;
        if (!LambdaGenerationUtil.canBeUncheckedLambda(condition)) return null;
        return condition;
      }

      @Override
      public void visitForStatement(PsiForStatement statement) {
        super.visitForStatement(statement);
        PsiStatement initialization = statement.getInitialization();
        IteratorDeclaration declaration = IteratorDeclaration.extract(initialization);
        if(declaration == null) return;
        if(statement.getUpdate() != null) return;
        if(!declaration.isHasNextCall(statement.getCondition())) return;
        handleIteratorLoop(statement, statement.getRParenth(), declaration);
      }

      @Override
      public void visitWhileStatement(PsiWhileStatement statement) {
        super.visitWhileStatement(statement);
        PsiElement previous = PsiTreeUtil.skipSiblingsBackward(statement, PsiComment.class, PsiWhiteSpace.class);
        if(!(previous instanceof PsiDeclarationStatement)) return;
        IteratorDeclaration declaration = IteratorDeclaration.extract((PsiStatement)previous);
        if(declaration == null || !declaration.isHasNextCall(statement.getCondition())) return;
        if(!ReferencesSearch.search(declaration.myIterator, declaration.myIterator.getUseScope()).forEach(ref -> {
          return PsiTreeUtil.isAncestor(statement, ref.getElement(), true);
        })) return;
        handleIteratorLoop(statement, statement.getRParenth(), declaration);
      }
    };
  }

  private static class ReplaceWithRemoveIfQuickFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return QuickFixBundle.message("java.8.collection.removeif.inspection.fix.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if(!(element instanceof PsiLoopStatement)) return;
      PsiLoopStatement loop = (PsiLoopStatement)element;
      IteratorDeclaration declaration;
      PsiElement previous = null;
      if(loop instanceof PsiForStatement) {
        declaration = IteratorDeclaration.extract(((PsiForStatement)loop).getInitialization());
      } else if(loop instanceof PsiWhileStatement) {
        previous = PsiTreeUtil.skipSiblingsBackward(loop, PsiComment.class, PsiWhiteSpace.class);
        if(!(previous instanceof PsiDeclarationStatement)) return;
        declaration = IteratorDeclaration.extract((PsiStatement)previous);
      } else return;
      if(declaration == null) return;
      PsiStatement body = loop.getBody();
      if(!(body instanceof PsiBlockStatement)) return;
      PsiStatement[] statements = ((PsiBlockStatement)body).getCodeBlock().getStatements();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      String replacement = null;
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
      if (statements.length == 2 && statements[1] instanceof PsiIfStatement) {
        PsiVariable variable = declaration.getNextElementVariable(statements[0]);
        if (variable == null) return;
        PsiExpression condition = ((PsiIfStatement)statements[1]).getCondition();
        if (condition == null) return;
        replacement = (declaration.myCollection == null ? "" : declaration.myCollection.getText() + ".") +
                             "removeIf(" + LambdaUtil.createLambda(variable, condition) + ");";
      }
      else if (statements.length == 1 && statements[0] instanceof PsiIfStatement){
        PsiExpression condition = ((PsiIfStatement)statements[0]).getCondition();
        if (condition == null) return;
        PsiElement ref = declaration.findOnlyIteratorRef(condition);
        if(ref != null) {
          PsiElement call = ref.getParent().getParent();
          if(!declaration.isIteratorMethodCall(call, "next")) return;
          PsiType type = ((PsiExpression)call).getType();
          JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
          SuggestedNameInfo info = javaCodeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, type);
          if(info.names.length == 0) {
            info = javaCodeStyleManager.suggestVariableName(VariableKind.PARAMETER, "value", null, type);
          }
          String paramName = javaCodeStyleManager.suggestUniqueVariableName(info, condition, true).names[0];
          call.replace(factory.createIdentifier(paramName));
          replacement = (declaration.myCollection == null ? "" : declaration.myCollection.getText() + ".") +
                        "removeIf(" + paramName + "->"+condition.getText() + ");";
        }
      }
      if(replacement == null) return;
      Collection<PsiComment> comments = ContainerUtil.map(PsiTreeUtil.findChildrenOfType(loop, PsiComment.class),
                                                          comment -> (PsiComment)comment.copy());
      PsiElement result = loop.replace(factory.createStatementFromText(replacement, loop));
      if (previous != null) previous.delete();
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      CodeStyleManager.getInstance(project).reformat(result);
      comments.forEach(comment -> result.getParent().addBefore(comment, result));
    }
  }

  private static class IteratorDeclaration {
    private final @NotNull PsiLocalVariable myIterator;
    private final @Nullable PsiExpression myCollection;

    private IteratorDeclaration(@NotNull PsiLocalVariable iterator, @Nullable PsiExpression collection) {
      myIterator = iterator;
      myCollection = collection;
    }

    public boolean isHasNextCall(PsiExpression condition) {
      return isIteratorMethodCall(condition, "hasNext");
    }

    @Nullable
    public PsiElement findOnlyIteratorRef(PsiExpression parent) {
      PsiElement element = PsiUtil.getVariableCodeBlock(myIterator, null);
      PsiCodeBlock block =
        element instanceof PsiCodeBlock ? (PsiCodeBlock)element : PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
      if(block == null) return null;
      return StreamEx.of(DefUseUtil.getRefs(block, myIterator, myIterator.getInitializer()))
                  .filter(e -> PsiTreeUtil.isAncestor(parent, e, false))
                  .collect(MoreCollectors.onlyOne()).orElse(null);
    }

    boolean isIteratorMethodCall(PsiElement candidate, String method) {
      if(!(candidate instanceof PsiMethodCallExpression)) return false;
      PsiMethodCallExpression call = (PsiMethodCallExpression)candidate;
      if(call.getArgumentList().getExpressions().length != 0) return false;
      PsiReferenceExpression expression = call.getMethodExpression();
      if(!method.equals(expression.getReferenceName())) return false;
      PsiExpression qualifier = expression.getQualifierExpression();
      if(!(qualifier instanceof PsiReferenceExpression)) return false;
      return ((PsiReferenceExpression)qualifier).resolve() == myIterator;
    }

    public PsiVariable getNextElementVariable(PsiStatement statement) {
      if(!(statement instanceof PsiDeclarationStatement)) return null;
      PsiDeclarationStatement declaration = (PsiDeclarationStatement)statement;
      if(declaration.getDeclaredElements().length != 1) return null;
      PsiElement element = declaration.getDeclaredElements()[0];
      if(!(element instanceof PsiLocalVariable)) return null;
      PsiLocalVariable var = (PsiLocalVariable)element;
      if(!isIteratorMethodCall(var.getInitializer(), "next")) return null;
      return var;
    }

    @Contract("null -> null")
    static IteratorDeclaration extract(PsiStatement statement) {
      if(!(statement instanceof PsiDeclarationStatement)) return null;
      PsiDeclarationStatement declaration = (PsiDeclarationStatement)statement;
      if(declaration.getDeclaredElements().length != 1) return null;
      PsiElement element = declaration.getDeclaredElements()[0];
      if(!(element instanceof PsiLocalVariable)) return null;
      PsiLocalVariable variable = (PsiLocalVariable)element;
      PsiExpression initializer = variable.getInitializer();
      if(!(initializer instanceof PsiMethodCallExpression)) return null;
      PsiMethodCallExpression call = (PsiMethodCallExpression)initializer;
      if(call.getArgumentList().getExpressions().length != 0) return null;
      PsiReferenceExpression methodExpression = call.getMethodExpression();
      if(!"iterator".equals(methodExpression.getReferenceName())) return null;
      PsiMethod method = call.resolveMethod();
      if(method == null || !InheritanceUtil.isInheritor(method.getContainingClass(), CommonClassNames.JAVA_UTIL_COLLECTION)) return null;
      PsiType type = variable.getType();
      if(!(type instanceof PsiClassType) || !((PsiClassType)type).rawType().equalsToText(CommonClassNames.JAVA_UTIL_ITERATOR)) return null;
      return new IteratorDeclaration(variable, methodExpression.getQualifierExpression());
    }
  }
}