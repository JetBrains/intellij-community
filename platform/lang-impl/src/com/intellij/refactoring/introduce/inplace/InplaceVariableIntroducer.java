/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.introduce.inplace;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageTokenSeparatorGenerators;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.rename.PreferrableNameSuggestionProvider;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.refactoring.rename.inplace.MyLookupExpression;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * User: anna
 * Date: 3/15/11
 */
public abstract class InplaceVariableIntroducer<E extends PsiElement> extends InplaceRefactoring {
 

  protected E myExpr;
  protected RangeMarker myExprMarker;

  protected E[] myOccurrences;
  protected List<RangeMarker> myOccurrenceMarkers;

 

  public InplaceVariableIntroducer(PsiNamedElement elementToRename,
                                   Editor editor,
                                   Project project,
                                   String title, E[] occurrences, 
                                   @Nullable E expr) {
    super(editor, elementToRename, project);
    myTitle = title;
    myOccurrences = occurrences;
    if (expr != null) {
      final ASTNode node = expr.getNode();
      final ASTNode astNode = LanguageTokenSeparatorGenerators.INSTANCE.forLanguage(expr.getLanguage())
        .generateWhitespaceBetweenTokens(node.getTreePrev(), node);
      if (astNode != null) {
        new WriteCommandAction<Object>(project, "Normalize declaration") {
          @Override
          protected void run(Result<Object> result) throws Throwable {
            node.getTreeParent().addChild(astNode, node);
          }
        }.execute();
      }
      myExpr = expr;
    }
    myExprMarker = myExpr != null && myExpr.isPhysical() ? createMarker(myExpr) : null;
    initOccurrencesMarkers();
  }

  @Override
  protected boolean shouldSelectAll() {
    return true;
  }

  @Override
  protected StartMarkAction startRename() throws StartMarkAction.AlreadyStartedException {
    return null;
  }

  
  public void setOccurrenceMarkers(List<RangeMarker> occurrenceMarkers) {
    myOccurrenceMarkers = occurrenceMarkers;
  }

  public void setExprMarker(RangeMarker exprMarker) {
    myExprMarker = exprMarker;
  }

  @Nullable
  public E getExpr() {
    return myExpr != null && myExpr.isValid() && myExpr.isPhysical() ? myExpr : null;
  }

  public E[] getOccurrences() {
    return myOccurrences;
  }

  public List<RangeMarker> getOccurrenceMarkers() {
    if (myOccurrenceMarkers == null) {
      initOccurrencesMarkers();
    }
    return myOccurrenceMarkers;
  }

  protected void initOccurrencesMarkers() {
    if (myOccurrenceMarkers != null) return;
    myOccurrenceMarkers = new ArrayList<RangeMarker>();
    for (E occurrence : myOccurrences) {
      myOccurrenceMarkers.add(createMarker(occurrence));
    }
  }

  protected RangeMarker createMarker(PsiElement element) {
    return myEditor.getDocument().createRangeMarker(element.getTextRange());
  }


  public RangeMarker getExprMarker() {
    return myExprMarker;
  }

  @Override
  protected boolean performRefactoring() {
    return false;
  }

  @Override
  protected void collectAdditionalElementsToRename(List<Pair<PsiElement, TextRange>> stringUsages) {
  }

  @Override
  protected String getCommandName() {
    return myTitle;
  }

  @Override
  protected void moveOffsetAfter(boolean success) {
    super.moveOffsetAfter(success);
    if (myOccurrenceMarkers != null) {
      for (RangeMarker marker : myOccurrenceMarkers) {
        marker.dispose();
      }
    }
    if (myExprMarker != null && !isRestart()) {
      myExprMarker.dispose();
    }
  }

 

  @Override
  protected MyLookupExpression createLookupExpression(PsiElement selectedElement) {
    return new MyIntroduceLookupExpression(getInitialName(), myNameSuggestions, myElementToRename, shouldSelectAll(), myAdvertisementText);
  }

  private static class MyIntroduceLookupExpression extends MyLookupExpression {
    private final SmartPsiElementPointer<PsiNamedElement> myPointer;

    public MyIntroduceLookupExpression(final String initialName,
                                       final LinkedHashSet<String> names,
                                       final PsiNamedElement elementToRename,
                                       final boolean shouldSelectAll,
                                       final String advertisementText) {
      super(initialName, names, elementToRename, elementToRename, shouldSelectAll, advertisementText);
      myPointer = SmartPointerManager.getInstance(elementToRename.getProject()).createSmartPsiElementPointer(elementToRename);
    }

    @Override
    public LookupElement[] calculateLookupItems(ExpressionContext context) {
      return createLookupItems(myName, context.getEditor(), getElement());
    }

    @Nullable
    public PsiNamedElement getElement() {
      return myPointer.getElement();
    }

    @Nullable
    private LookupElement[] createLookupItems(String name, Editor editor, PsiNamedElement psiVariable) {
      TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
      if (psiVariable != null) {
        final TextResult insertedValue =
          templateState != null ? templateState.getVariableValue(PRIMARY_VARIABLE_NAME) : null;
        if (insertedValue != null) {
          final String text = insertedValue.getText();
          if (!text.isEmpty() && !Comparing.strEqual(text, name)) {
            final LinkedHashSet<String> names = new LinkedHashSet<String>();
            names.add(text);
            for (NameSuggestionProvider provider : Extensions.getExtensions(NameSuggestionProvider.EP_NAME)) {
              final SuggestedNameInfo suggestedNameInfo = provider.getSuggestedNames(psiVariable, psiVariable, names);
              if (suggestedNameInfo != null &&
                  provider instanceof PreferrableNameSuggestionProvider &&
                  !((PreferrableNameSuggestionProvider)provider).shouldCheckOthers()) {
                break;
              }
            }
            final LookupElement[] items = new LookupElement[names.size()];
            final Iterator<String> iterator = names.iterator();
            for (int i = 0; i < items.length; i++) {
              items[i] = LookupElementBuilder.create(iterator.next());
            }
            return items;
          }
        }
      }
      return myLookupItems;
    }
  }
}
