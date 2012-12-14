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
package com.intellij.find.impl.livePreview;

import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;

public class ReplacementView extends JPanel {

  private static final String MALFORMED_REPLACEMENT_STRING = "Malformed replacement string";
  private String myReplacement;
  private LiveOccurrence myOccurrence;
  private JButton myStatusButton;

  public interface Delegate {
    void performReplacement(LiveOccurrence occurrence, String replacement);
    void performReplaceAll();
    boolean isExcluded(LiveOccurrence occurrence);
    void exclude(LiveOccurrence occurrence);
  }

  private Delegate myDelegate;

  public Delegate getDelegate() {
    return myDelegate;
  }

  public void setDelegate(Delegate delegate) {
    this.myDelegate = delegate;
  }

  @Override
  protected void paintComponent(Graphics graphics) {

  }

  public ReplacementView(final String replacement, final LiveOccurrence occurrence) {
    myReplacement = replacement;
    String textToShow = myReplacement;
    if (myReplacement == null) {
      textToShow = MALFORMED_REPLACEMENT_STRING;
    }
    JLabel jLabel = new JLabel(textToShow);
    jLabel.setForeground(myReplacement != null ? Color.WHITE : JBColor.RED);
    add(jLabel);
  }
}
