// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ui.ClientProperty;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.BW;
import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.COMPACT_HEIGHT;
import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.COMPONENT_ARC;
import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.isCompact;
import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.isTableCellEditor;
import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.paddings;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTextFieldUI extends TextFieldWithPopupHandlerUI {

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaTextFieldUI();
  }

  @Override
  protected int getMinimumHeight(int textHeight) {
    JComponent c = getComponent();
    if (DarculaEditorTextFieldBorder.isComboBoxEditor(c) ||
        ComponentUtil.getParentOfType((Class<? extends JSpinner>)JSpinner.class, (Component)c) != null ||
        ClientProperty.isTrue(c, "TextField.NoMinHeightBounds")) {
      return textHeight;
    }

    Insets i = getComponent().getInsets();
    return (isCompact(c) ? COMPACT_HEIGHT.get() : JBUI.CurrentTheme.TextField.minimumSize().height)+ i.top + i.bottom;
  }

  @Override
  protected int getClearIconPreferredSpace() {
    return super.getClearIconPreferredSpace() - getClearIconGap();
  }

  @Override
  protected void paintBackground(Graphics g) {
    JTextComponent component = getComponent();
    if (component != null) {
      Container parent = component.getParent();
      if (parent != null && component.isOpaque()) {
        g.setColor(parent.getBackground());
        g.fillRect(0, 0, component.getWidth(), component.getHeight());
      }

      if (component.getBorder() instanceof DarculaTextBorder && !isTableCellEditor(component)) {
        paintDarculaBackground(g, component);
      }
      else if (component.isOpaque()) {
        super.paintBackground(g);
      }
    }
  }

  protected void paintDarculaBackground(Graphics g, JTextComponent component) {
    if (component.getClientProperty(JBTextField.IS_FORCE_INNER_BACKGROUND_PAINT) != Boolean.TRUE &&
        (!component.isEnabled() || !component.isEditable())) {
      return;
    }

    Graphics2D g2 = (Graphics2D)g.create();
    Rectangle r = new Rectangle(component.getSize());
    JBInsets.removeFrom(r, paddings());

    try {
      var darculaTextBorderNew = getDarculaTextBorderNew(component);
      if (darculaTextBorderNew != null) {
        darculaTextBorderNew.paintTextBackground(g2, component, component.getBackground());
        return;
      }

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

      g2.translate(r.x, r.y);

      float arc = isSearchField(component) ? COMPONENT_ARC.getFloat() : 0.0f;
      float bw = bw();

      g2.setColor(component.getBackground());
      g2.fill(new RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc));
    }
    finally {
      g2.dispose();
    }
  }

  @Override
  protected Insets getDefaultMargins() {
    Component c = getComponent();
    if (isCompact(c) || isTableCellEditor(c)) {
      return JBInsets.create(0, 3);
    }

    return getDarculaTextBorderNew(c) == null ? JBInsets.create(2, 6) : new JBInsets(2, 9, 2, 6);
  }

  protected float bw() {
    return BW.getFloat();
  }

  private static DarculaTextBorderNew getDarculaTextBorderNew(@Nullable Component c) {
    return c instanceof JComponent jComponent && jComponent.getBorder() instanceof DarculaTextBorderNew darculaTextBorder
           ? darculaTextBorder
           : null;
  }
}
