package com.michaelbaranov.microba.gradienteditor.ui;

import com.michaelbaranov.microba.gradient.GradientBar;
import com.michaelbaranov.microba.gradienteditor.GradientEditor;
import com.michaelbaranov.microba.marker.MarkerBar;

import javax.swing.*;
import java.awt.*;

public class GradientEditorLayout implements LayoutManager {

  private final MarkerBar bar;

  private final GradientBar gradient;

  public GradientEditorLayout(MarkerBar bar, GradientBar gradient) {
    this.bar = bar;
    this.gradient = gradient;
  }

  @Override
  public void addLayoutComponent(String name, Component comp) {
  }

  @Override
  public void removeLayoutComponent(Component comp) {
  }

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    return parent.getPreferredSize();
  }

  @Override
  public Dimension minimumLayoutSize(Container parent) {
    return parent.getMinimumSize();
  }

  @Override
  public void layoutContainer(Container parent) {
    GradientEditor e = (GradientEditor) parent;
    Insets i = parent.getInsets();
    int gap = bar.getMarkerSideGap();
    if (e.getOrientation() == SwingConstants.HORIZONTAL) {
      bar.setBounds(i.left, i.top, parent.getWidth() - i.left - i.right,
          bar.getPreferredSize().height);
      gradient.setBounds(i.left + gap, bar.getBounds().y
          + bar.getBounds().height, parent.getWidth() - i.left
          - i.right - gap - gap, parent.getHeight() - i.top
          - i.bottom - bar.getHeight());
    } else {
      gradient.setBounds(i.left, i.top+gap, 
          gradient.getPreferredSize().width,parent.getHeight() - i.top - i.bottom-gap-gap);
      bar.setBounds(gradient.getBounds().x+gradient.getBounds().width ,i.top,
          bar.getPreferredSize().width,gradient.getBounds().height+gap+gap);
      
    }
  }

}
