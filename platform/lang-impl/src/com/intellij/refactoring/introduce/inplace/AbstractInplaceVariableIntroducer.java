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
import com.intellij.codeInsight.template.TextResult;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * User: anna
 * Date: 3/15/11
 */
public abstract class AbstractInplaceVariableIntroducer<E extends PsiElement> extends VariableInplaceRenamer {
  public static final Key<Boolean> INTRODUCE_RESTART = Key.create("INTRODUCE_RESTART");

  protected E myExpr;
  protected RangeMarker myExprMarker;

  protected E[] myOccurrences;
  protected List<RangeMarker> myOccurrenceMarkers;

  protected Balloon myBalloon;
  protected String myTitle;
  protected RelativePoint myTarget;

  public AbstractInplaceVariableIntroducer(PsiNamedElement elementToRename,
                                           Editor editor,
                                           Project project,
                                           String title, E[] occurrences, E expr) {
    super(elementToRename, editor, project);
    myTitle = title;
    myOccurrences = occurrences;
    myExpr = expr;
    myExprMarker = myExpr != null && myExpr.isPhysical() ? myEditor.getDocument().createRangeMarker(myExpr.getTextRange()) : null;
    initOccurrencesMarkers();
  }

  protected abstract JComponent getComponent();

  public void setOccurrenceMarkers(List<RangeMarker> occurrenceMarkers) {
    myOccurrenceMarkers = occurrenceMarkers;
  }

  public void setExprMarker(RangeMarker exprMarker) {
    myExprMarker = exprMarker;
  }

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
      myOccurrenceMarkers.add(myEditor.getDocument().createRangeMarker(occurrence.getTextRange()));
    }
  }


  public RangeMarker getExprMarker() {
    return myExprMarker;
  }

  @Override
  public boolean performInplaceRename(boolean processTextOccurrences, LinkedHashSet<String> nameSuggestions) {
    final boolean result = super.performInplaceRename(processTextOccurrences, nameSuggestions);
    if (result) {
      if (myBalloon == null) {
        showBalloon();
      }
    }
    return result;
  }

  private void showBalloon() {
    final JComponent component = getComponent();
    if (component == null) return;
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    final BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createBalloonBuilder(component);
    balloonBuilder.setFadeoutTime(0)
      .setFillColor(UIManager.getColor("Panel.background"))
      .setAnimationCycle(100)
      .setHideOnClickOutside(false)
      .setHideOnKeyOutside(false)
      .setHideOnAction(false)
      .setCloseButtonEnabled(true);

    final RelativePoint target = JBPopupFactory.getInstance().guessBestPopupLocation(myEditor);
    final Point screenPoint = target.getScreenPoint();
    myBalloon = balloonBuilder.createBalloon();
    int y = screenPoint.y;
    if (target.getPoint().getY() > myEditor.getLineHeight() + myBalloon.getPreferredSize().getHeight()) {
      y -= myEditor.getLineHeight();
    }
    myTarget = new RelativePoint(new Point(screenPoint.x, y));
    myBalloon.show(myTarget, Balloon.Position.above);
  }

  @Override
  public void finish() {
    super.finish();
    if (myBalloon != null) {
      final Boolean isRestart = myEditor.getUserData(INTRODUCE_RESTART);
      if (isRestart == null || !isRestart.booleanValue()) {
        myBalloon.hide();
      }
    }
  }

  @Override
  protected LookupElement[] createLookupItems(LookupElement[] lookupItems, String name) {
    TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
    final PsiNamedElement psiVariable = getVariable();
    if (psiVariable != null) {
      final TextResult insertedValue =
        templateState != null ? templateState.getVariableValue(PRIMARY_VARIABLE_NAME) : null;
      if (insertedValue != null) {
        final String text = insertedValue.getText();
        if (!text.isEmpty() && !Comparing.strEqual(text, name)) {
          final LinkedHashSet<String> names = new LinkedHashSet<String>();
          names.add(text);
          for (NameSuggestionProvider provider : Extensions.getExtensions(NameSuggestionProvider.EP_NAME)) {
            provider.getSuggestedNames(psiVariable, psiVariable, names);
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
    return super.createLookupItems(lookupItems, name);
  }

  @Override
  protected TextRange preserveSelectedRange(SelectionModel selectionModel) {
    return null;
  }
}
