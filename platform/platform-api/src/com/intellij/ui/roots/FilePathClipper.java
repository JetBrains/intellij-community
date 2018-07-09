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
package com.intellij.ui.roots;

import com.intellij.util.ui.FilePathSplittingPolicy;

import javax.swing.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 */
public class FilePathClipper implements ComponentListener {
  private final File myFile;
  private final JLabel myLabelToClip;
  private final JComponent myComponentToWatch;

  private FilePathClipper(JLabel labelToClip, JComponent componentToWatch) {
    myLabelToClip = labelToClip;
    myComponentToWatch = componentToWatch;
    final String text = labelToClip.getText(); // newly created JLabel can return null here
    myFile = new File(text != null? text : "");
  }

  public static void install(JLabel labelToClip, JComponent componentToWatch) {
    componentToWatch.addComponentListener(new FilePathClipper(labelToClip, componentToWatch));
  }

  public void componentResized(ComponentEvent e) {
    final String optimalTextForComponent = FilePathSplittingPolicy.SPLIT_BY_SEPARATOR.getOptimalTextForComponent(myFile, myLabelToClip, myComponentToWatch.getWidth());
    myLabelToClip.setText(optimalTextForComponent);
  }

  public void componentHidden(ComponentEvent e) {}
  public void componentMoved(ComponentEvent e)  {}
  public void componentShown(ComponentEvent e)  {}
}
