// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.roots;

import com.intellij.util.ui.FilePathSplittingPolicy;

import javax.swing.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 */
public final class FilePathClipper implements ComponentListener {
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

  @Override
  public void componentResized(ComponentEvent e) {
    final String optimalTextForComponent = FilePathSplittingPolicy.SPLIT_BY_SEPARATOR.getOptimalTextForComponent(myFile, myLabelToClip, myComponentToWatch.getWidth());
    myLabelToClip.setText(optimalTextForComponent);
  }

  @Override
  public void componentHidden(ComponentEvent e) {}
  @Override
  public void componentMoved(ComponentEvent e)  {}
  @Override
  public void componentShown(ComponentEvent e)  {}
}
