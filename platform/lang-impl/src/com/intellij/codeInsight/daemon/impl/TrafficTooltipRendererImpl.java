/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.LineTooltipRenderer;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl;
import com.intellij.openapi.editor.impl.TrafficTooltipRenderer;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;

import java.awt.*;

/**
 * User: cdr
 */
public class TrafficTooltipRendererImpl implements TrafficTooltipRenderer {
  private TrafficProgressPanel myPanel;
  private final Runnable onHide;
  private TrafficLightRenderer myTrafficLightRenderer;

  public TrafficTooltipRendererImpl(Runnable onHide) {
    this.onHide = onHide;
  }

  @Override
  public void repaintTooltipWindow() {
    if (myPanel != null) {
      myPanel.updatePanel(myTrafficLightRenderer.getDaemonCodeAnalyzerStatus(true,
                                                                             SeverityRegistrar.getInstance(
                                                                               myTrafficLightRenderer.getProject())));
    }
  }

  @Override
  public LightweightHint show(Editor editor, Point p, boolean alignToRight, TooltipGroup group, HintHint hintHint) {
    myTrafficLightRenderer = (TrafficLightRenderer)((EditorMarkupModelImpl)editor.getMarkupModel()).getErrorStripeRenderer();
    myPanel = new TrafficProgressPanel(myTrafficLightRenderer, editor, hintHint);
    LineTooltipRenderer.correctLocation(editor, myPanel, p, alignToRight, false, -1);
    LightweightHint hint = new LightweightHint(myPanel) {
      @Override
      public void hide() {
        if (myPanel == null) return; //double hide?
        super.hide();
        myPanel = null;
        onHide.run();
      }
    };
    HintManagerImpl hintManager = (HintManagerImpl)HintManager.getInstance();
    hintManager.showEditorHint(hint, editor, p,
                               HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_OTHER_HINT |
                               HintManager.HIDE_BY_SCROLLING, 0, false, hintHint);
    repaintTooltipWindow();
    return hint;
  }

  @Override
  public boolean equals(Object obj) {
    return obj.getClass() == getClass() && myTrafficLightRenderer == ((TrafficTooltipRendererImpl)obj).myTrafficLightRenderer;
  }
}
