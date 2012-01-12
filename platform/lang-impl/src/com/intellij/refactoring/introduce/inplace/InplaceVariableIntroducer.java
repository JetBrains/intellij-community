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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.PositionTracker;
import org.jetbrains.annotations.Nullable;

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
public abstract class InplaceVariableIntroducer<E extends PsiElement> extends InplaceRefactoring {
  public static final Key<Boolean> INTRODUCE_RESTART = Key.create("INTRODUCE_RESTART");

  protected E myExpr;
  protected RangeMarker myExprMarker;

  protected E[] myOccurrences;
  protected List<RangeMarker> myOccurrenceMarkers;

  protected Balloon myBalloon;
  protected String myTitle;
  protected RelativePoint myTarget;
  private RangeMarker myCaretRangeMarker;

  public InplaceVariableIntroducer(PsiNamedElement elementToRename,
                                   Editor editor,
                                   Project project,
                                   String title, E[] occurrences, 
                                   @Nullable E expr) {
    super(editor, elementToRename, project);
    myTitle = title;
    myOccurrences = occurrences;
    myExpr = expr;
    myExprMarker = myExpr != null && myExpr.isPhysical() ? createMarker(myExpr) : null;
    initOccurrencesMarkers();
  }

  @Override
  protected boolean shouldSelectAll() {
    return true;
  }

  @Override
  protected int restoreCaretOffset(int offset) {
    return myCaretRangeMarker.isValid() ? myCaretRangeMarker.getStartOffset() : offset;
  }

  @Override
  protected StartMarkAction startRename() throws StartMarkAction.AlreadyStartedException {
    return null;
  }

  @Nullable
  protected JComponent getComponent() {
    return null;
  }

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
  protected void beforeTemplateStart() {
    myCaretRangeMarker = myEditor.getDocument()
          .createRangeMarker(new TextRange(myEditor.getCaretModel().getOffset(), myEditor.getCaretModel().getOffset()));
  }

  @Override
  protected void collectAdditionalElementsToRename(List<Pair<PsiElement, TextRange>> stringUsages) {
  }

  @Override
  protected String getCommandName() {
    return myTitle;
  }

  @Override
  public boolean performInplaceRefactoring(LinkedHashSet<String> nameSuggestions) {
    final boolean result = super.performInplaceRefactoring(nameSuggestions);
    if (result) {
      if (myBalloon == null) {
        showBalloon();
      }
    }
    return result;
  }

  protected void releaseResources() {

  }

  protected void showBalloon() {
    final JComponent component = getComponent();
    if (component == null) return;
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    final BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createDialogBalloonBuilder(component, null).setSmallVariant(true);
    myBalloon = balloonBuilder.createBalloon();
    Disposer.register(myProject, myBalloon);
    Disposer.register(myBalloon, new Disposable() {
      @Override
      public void dispose() {
        releaseIfNotRestart();
      }
    });
    myBalloon.show(new PositionTracker<Balloon>(myEditor.getContentComponent()) {
      @Override
      public RelativePoint recalculateLocation(Balloon object) {
        final RelativePoint target = JBPopupFactory.getInstance().guessBestPopupLocation(myEditor);
        final Point screenPoint = target.getScreenPoint();
        int y = screenPoint.y;
        if (target.getPoint().getY() > myEditor.getLineHeight() + myBalloon.getPreferredSize().getHeight()) {
          y -= myEditor.getLineHeight();
        }
        myTarget = new RelativePoint(new Point(screenPoint.x, y));
        return myTarget;
      }
    }, Balloon.Position.above);
  }

  protected void releaseIfNotRestart() {
    final Boolean isRestart = myEditor.getUserData(INTRODUCE_RESTART);
    if (isRestart == null || !isRestart.booleanValue()) {
      releaseResources();
    }
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
    return lookupItems;
  }

}
