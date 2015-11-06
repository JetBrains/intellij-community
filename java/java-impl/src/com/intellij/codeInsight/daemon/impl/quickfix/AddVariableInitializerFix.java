/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.*;

public class AddVariableInitializerFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddReturnFix");
  private final PsiVariable myVariable;

  public AddVariableInitializerFix(@NotNull PsiVariable variable) {
    myVariable = variable;
  }

  @Override
  @NotNull
  public String getText() {
    return CodeInsightBundle.message("quickfix.add.variable.text", myVariable.getName());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("quickfix.add.variable.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myVariable.isValid() &&
           myVariable.getManager().isInProject(myVariable) &&
           !myVariable.hasInitializer() &&
           !(myVariable instanceof PsiParameter)
        ;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(myVariable.getContainingFile())) return;

    final LookupElement[] suggestedInitializers = suggestInitializer(myVariable);
    LOG.assertTrue(suggestedInitializers.length > 0);
    LOG.assertTrue(suggestedInitializers[0] instanceof ExpressionLookupItem);
    final PsiExpression initializer = (PsiExpression) suggestedInitializers[0].getObject();
    if (myVariable instanceof PsiLocalVariable) {
      ((PsiLocalVariable)myVariable).setInitializer(initializer);
    }
    else if (myVariable instanceof PsiField) {
      ((PsiField)myVariable).setInitializer(initializer);
    }
    else {
      LOG.error("Unknown variable type: "+myVariable);
    }
    runAssignmentTemplate(Collections.singletonList(myVariable.getInitializer()), suggestedInitializers, editor);
  }

  public static void runAssignmentTemplate(@NotNull final List<PsiExpression> initializers,
                                           @NotNull final LookupElement[] suggestedInitializers,
                                           @Nullable Editor editor) {
    if (editor == null) return;
    LOG.assertTrue(!initializers.isEmpty());
    final PsiExpression initializer = ContainerUtil.getFirstItem(initializers);
    PsiElement context = initializers.size() == 1 ? initializer : PsiTreeUtil.findCommonParent(initializers);
    PsiDocumentManager.getInstance(initializer.getProject()).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    final TemplateBuilderImpl builder = (TemplateBuilderImpl)TemplateBuilderFactory.getInstance().createTemplateBuilder(context);
    for (PsiExpression e : initializers) {
      builder.replaceElement(e, new Expression() {
        @Nullable
        @Override
        public Result calculateResult(ExpressionContext context1) {
          return calculateQuickResult(context1);
        }

        @Nullable
        @Override
        public Result calculateQuickResult(ExpressionContext context1) {
          return new PsiElementResult(suggestedInitializers[0].getPsiElement());
        }

        @Nullable
        @Override
        public LookupElement[] calculateLookupItems(ExpressionContext context1) {
          return suggestedInitializers;
        }
      });
    }
    builder.run(editor, false);
  }

  @NotNull
  public static LookupElement[] suggestInitializer(final PsiVariable variable) {
    PsiType type = variable.getType();
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(variable.getProject());
    if (type instanceof PsiClassType) {
      final PsiClass aClass = PsiTypesUtil.getPsiClass(type);
      if (aClass != null) {
        final LookupElement nullLookupItem = new ExpressionLookupItem(elementFactory.createExpressionFromText(PsiKeyword.NULL, variable));
        if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_ITERABLE) ||
            InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_MAP)) {
          final List<PsiType> suggestedTypes = new SmartList<PsiType>();
          JavaInheritorsGetter.processInheritors(variable.getContainingFile(), variable, Collections.singleton((PsiClassType) type), PrefixMatcher.ALWAYS_TRUE, new Consumer<PsiType>() {
            @Override
            public void consume(PsiType type) {
              LOG.assertTrue(type instanceof PsiClassType);
              final PsiClass psiClass = PsiTypesUtil.getPsiClass(type);
              if (psiClass != null &&
                  !psiClass.isInterface() &&
                  !psiClass.hasModifierProperty(PsiModifier.ABSTRACT) &&
                  PsiUtil.hasDefaultConstructor(psiClass)) {
                suggestedTypes.add(type);
              }
            }
          });

          List<LookupElement> sortedLookups = map(sorted(map(suggestedTypes, new Function<PsiType, LookupElement>() {
            @Override
            public LookupElement fun(PsiType type) {
              return PsiTypeLookupItem.createLookupItem(type, variable);
            }
          }), new Comparator<LookupElement>() {
            @Override
            public int compare(LookupElement o1, LookupElement o2) {
              final int count1 = StatisticsWeigher.getBaseStatisticsInfo(o1, null).getUseCount();
              final int count2 = StatisticsWeigher.getBaseStatisticsInfo(o2, null).getUseCount();
              return count2 - count1;
            }
          }), new Function<LookupElement, LookupElement>() {
            @Override
            public LookupElement fun(LookupElement element) {
              final LookupElementDecorator<LookupElement> constructorLookupElement =
                LookupElementDecorator.withInsertHandler(element, ConstructorInsertHandler.BASIC_INSTANCE);
              return new LookupElementDecorator<LookupElement>(constructorLookupElement) {
                @Override
                public void renderElement(LookupElementPresentation presentation) {
                  super.renderElement(presentation);
                  presentation.setTailText("");
                  presentation.setItemText(PsiKeyword.NEW + " " + presentation.getItemText() + "()");
                }

                @Override
                public void handleInsert(InsertionContext context) {
                  super.handleInsert(context);
                  context.getDocument().insertString(context.getStartOffset(), PsiKeyword.NEW + " ");
                }
              };
            }
          });
          LookupElement[] result = new LookupElement[sortedLookups.size() + 1];
          result[0] = nullLookupItem;
          for (int i = 0; i < sortedLookups.size(); i++) {
            LookupElement lookup = sortedLookups.get(i);
            result[i + 1] = lookup;
          }
          return result;
        } else {
          if (PsiUtil.hasDefaultConstructor(aClass)) {
            final PsiExpression newExpression = elementFactory
              .createExpressionFromText(PsiKeyword.NEW + " " + type.getCanonicalText(false) + "()", variable);
            return new LookupElement[]{nullLookupItem, new ExpressionLookupItem(newExpression)};
          }
        }
      }
    }
    final String defaultValue = PsiTypesUtil.getDefaultValueOfType(type);
    final PsiExpression expression = elementFactory.createExpressionFromText(defaultValue, variable);
    return new LookupElement[] {new ExpressionLookupItem(expression)};
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
