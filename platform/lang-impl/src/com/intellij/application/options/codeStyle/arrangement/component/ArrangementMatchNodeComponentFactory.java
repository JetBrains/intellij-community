/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement.component;

import com.intellij.application.options.codeStyle.arrangement.util.ArrangementAnimationPanel;
import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.application.options.codeStyle.arrangement.ArrangementNodeDisplayManager;
import com.intellij.application.options.codeStyle.arrangement.ArrangementMatchingRulesList;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchConditionVisitor;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 8/10/12 2:53 PM
 */
public class ArrangementMatchNodeComponentFactory {
  
  @NotNull private static final Object DUMMY_ELEMENT = new Object();

  @NotNull private final ArrangementNodeDisplayManager myDisplayManager;
  @NotNull private final ArrangementColorsProvider     myColorsProvider;
  @NotNull private final ArrangementMatchingRulesList  myList;

  public ArrangementMatchNodeComponentFactory(@NotNull ArrangementNodeDisplayManager manager,
                                              @NotNull ArrangementColorsProvider provider,
                                              @NotNull ArrangementMatchingRulesList list)
  {
    myDisplayManager = manager;
    myColorsProvider = provider;
    myList = list;
  }

  /**
   * Allows to build UI component for the given model.
   *
   * @param rendererTarget      target model element for which UI component should be built
   * @param rule                rule which contains given 'renderer target' condition and serves as
   *                            a data entry for the target list model
   * @param allowModification   flag which indicates whether given model can be changed at future
   * @return renderer for the given model
   */
  @NotNull
  public ArrangementMatchConditionComponent getComponent(@NotNull final ArrangementMatchCondition rendererTarget,
                                                         @NotNull final StdArrangementMatchRule rule,
                                                         final boolean allowModification)
  {
    final Ref<ArrangementMatchConditionComponent> ref = new Ref<ArrangementMatchConditionComponent>();
    rendererTarget.invite(new ArrangementMatchConditionVisitor() {
      @Override
      public void visit(@NotNull ArrangementAtomMatchCondition condition) {
        RemoveAtomConditionCallback callback = allowModification ? new RemoveAtomConditionCallback(rule) : null;
        ArrangementMatchConditionComponent component = new ArrangementAtomMatchConditionComponent(
          myDisplayManager, myColorsProvider, condition, callback
        );
        ref.set(component);
      }

      @Override
      public void visit(@NotNull ArrangementCompositeMatchCondition condition) {
        ref.set(new ArrangementAndMatchConditionComponent(rule, condition, ArrangementMatchNodeComponentFactory.this, myDisplayManager));
      }
    });
    return ref.get();
  }

  private class RemoveAtomConditionCallback implements Consumer<ArrangementAtomMatchConditionComponent>,
                                                       ArrangementAnimationPanel.Listener,
                                                       ActionListener
  {

    @NotNull private final Timer myTimer = new Timer(ArrangementConstants.ANIMATION_STEPS_TIME_GAP_MILLIS, this);

    @NotNull private final StdArrangementMatchRule   myRule;
    @NotNull private       ArrangementAnimationPanel myAnimationPanel;

    private int myRow;

    RemoveAtomConditionCallback(@NotNull StdArrangementMatchRule rule) {
      myRule = rule;
    }

    @Override
    public void consume(@NotNull ArrangementAtomMatchConditionComponent component) {
      ArrangementAtomMatchCondition condition = component.getMatchCondition();
      DefaultListModel model = (DefaultListModel)myList.getModel();
      int i = model.indexOf(myRule);
      if (i < 0) {
        return;
      }
      myRow = i;

      ArrangementMatchCondition existingCondition = myRule.getMatcher().getCondition();
      if (existingCondition.equals(condition)) {
        // We can't just remove an element at this time because that breaks last row rendering. 
        model.set(i, DUMMY_ELEMENT);
      }
      else {
        assert existingCondition instanceof ArrangementCompositeMatchCondition;
        Set<ArrangementMatchCondition> operands = ((ArrangementCompositeMatchCondition)existingCondition).getOperands();
        operands.remove(condition);
        if (operands.isEmpty()) {
          // We can't just remove an element at this time because that breaks last row rendering.
          model.set(i, DUMMY_ELEMENT);
        }
        else if (operands.size() == 1) {
          model.set(i, new StdArrangementMatchRule(new StdArrangementEntryMatcher(operands.iterator().next()), myRule.getOrderType()));
        }
      }

      myAnimationPanel = component.getAnimationPanel();
      myAnimationPanel.setListener(this);
      myAnimationPanel.startAnimation(false, true);
      myTimer.stop();
      myList.repaintRows(i, i, false);
    }

    @Override
    public void onPaint() {
      if (myTimer.isRunning()) {
        return;
      }
      myTimer.restart();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      boolean continueAnimation = myAnimationPanel.nextIteration();
      myList.repaintRows(myRow, myRow, !continueAnimation);
      myTimer.stop();
      if (continueAnimation) {
        return;
      }
      DefaultListModel model = (DefaultListModel)myList.getModel();
      boolean repaintToBottom = model.indexOf(myRule) < 0;
      if (repaintToBottom) {
        Object removeCandidate = model.getElementAt(myRow);
        if (removeCandidate == DUMMY_ELEMENT) {
          model.remove(myRow);
        }
      }

      if (repaintToBottom && myRow < model.getSize()) {
        myList.repaintRows(myRow, model.getSize() - 1, true);
      }
    }
  }
}
