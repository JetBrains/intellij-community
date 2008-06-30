package com.intellij.ui.roots;

import com.intellij.util.ui.FilePathSplittingPolicy;

import javax.swing.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 20, 2004
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
