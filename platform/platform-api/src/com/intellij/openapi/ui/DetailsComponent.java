/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.openapi.ui;

import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;

public class DetailsComponent {

  private final JPanel myComponent;

  private JComponent myContent;


  private final Banner myBannerLabel;

  private final JLabel myEmptyContentLabel;
  private final NonOpaquePanel myBanner;

  private String[] myBannerText;
  private boolean myDetailsEnabled = true;
  private String[] myPrefix;
  private String[] myText;

  private final Wrapper myContentGutter = new Wrapper();

  private boolean myPaintBorder = true;

  public DetailsComponent() {
    myComponent = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(final Graphics g) {
        if (NullableComponent.Check.isNull(myContent) || !myDetailsEnabled) return;

        GraphicsConfig c = new GraphicsConfig(g);
        c.setAntialiasing(true);

        Insets insets = getInsets();
        if (insets == null) {
          insets = new Insets(0, 0, 0, 0);
        }

        g.setColor(UIUtil.getFocusedFillColor());

        final Rectangle banner = myBanner.getBounds();
        final GeneralPath header = new GeneralPath();

        final int leftX = insets.left;
        final int leftY = insets.top;
        final int rightX = insets.left + getWidth() - 1 - insets.right;
        final int rightY = banner.y + banner.height;

        header.moveTo(leftX, rightY);
        int arc = 8;
        header.lineTo(leftX, leftY + arc);
        header.quadTo(leftX, leftY, leftX + arc, leftY);
        header.lineTo(rightX - arc, leftY);
        header.quadTo(rightX, leftY, rightX, leftY + arc);
        header.lineTo(rightX, rightY);
        header.closePath();

        c.getG().fill(header);

        g.setColor(UIUtil.getFocusedBoundsColor());

        c.getG().draw(header);

        if (myPaintBorder) {
          final int down = getHeight() - insets.top - insets.bottom - 1;
          g.drawLine(leftX, rightY, leftX, down);
          g.drawLine(rightX, rightY, rightX, down);
          g.drawLine(leftX, down, rightX, down);
        }

        c.restore();
      }
    };

    myComponent.setOpaque(false);
    myContentGutter.setOpaque(false);
    myContentGutter.setBorder(null);

    myBanner = new NonOpaquePanel(new BorderLayout());
    myBannerLabel = new Banner();

    myBanner.add(myBannerLabel, BorderLayout.CENTER);

    myEmptyContentLabel = new JLabel("", SwingConstants.CENTER);

    revalidateDetailsMode();
  }

  private void revalidateDetailsMode() {
    myComponent.removeAll();
    myComponent.add(myContentGutter, BorderLayout.CENTER);

    if (myDetailsEnabled) {
      myComponent.add(myBanner, BorderLayout.NORTH);
    }

    if (myContent != null) {
      myContentGutter.add(myContent, BorderLayout.CENTER);
      invalidateContentBorder();
    }

    myComponent.revalidate();
    myComponent.repaint();
  }

  public void setBannerActions(Action[] actions) {
    myBannerLabel.clearActions();
    for (Action each : actions) {
      myBannerLabel.addAction(each);
    }

    myComponent.revalidate();
    myComponent.repaint();
  }

  public void setContent(@Nullable JComponent c) {
    if (myContent != null) {
      myContentGutter.remove(myContent);
    }

    myContent = new MyWrapper(c);

    myContent.setOpaque(false);

    invalidateContentBorder();

    myContentGutter.add(myContent, BorderLayout.CENTER);

    updateBanner();

    myComponent.revalidate();
    myComponent.repaint();
  }

  private void invalidateContentBorder() {
    if (myDetailsEnabled) {
      myContent.setBorder(new EmptyBorder(UIUtil.PANEL_REGULAR_INSETS));
    }
    else {
      myContent.setBorder(null);
    }
  }


  public void setPrefix(@Nullable String... prefix) {
    myPrefix = prefix;
    if (myText != null) {
      setText(myText);
    }
  }

  public void setText(@Nullable String... text) {
    myText = text;
    update();
  }

  public void update() {
    ArrayList<String> strings = new ArrayList<String>();
    if (myPrefix != null) {
      ContainerUtil.addAll(strings, myPrefix);
    }

    if (myText != null) {
      ContainerUtil.addAll(strings, myText);
    }

    myBannerText = ArrayUtil.toStringArray(strings);

    updateBanner();
  }

  private void updateBanner() {
    myBannerLabel.setText(NullableComponent.Check.isNull(myContent) || myBannerText == null ? ArrayUtil.EMPTY_STRING_ARRAY : myBannerText);

    myBannerLabel.revalidate();
    myBannerLabel.repaint();
  }

  public void setPaintBorder(final boolean paintBorder) {
    myPaintBorder = paintBorder;
  }

  public DetailsComponent setEmptyContentText(@Nullable final String emptyContentText) {
    @NonNls final String s = XmlStringUtil.wrapInHtml("<center>" + (emptyContentText != null ? emptyContentText : "") + "</center>");
    myEmptyContentLabel.setText(s);
    return this;
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public JComponent getContentGutter() {
    return myContentGutter;
  }

  public void setBannerMinHeight(final int height) {
    myBannerLabel.setMinHeight(height);
  }

  public void disposeUIResources() {
    setContent(null);
  }

  public void updateBannerActions() {
    myBannerLabel.updateActions();
  }

  public void setDetailsModeEnabled(final boolean enabled) {
    if (myDetailsEnabled == enabled) return;

    myDetailsEnabled = enabled;

    revalidateDetailsMode();
  }


  public interface Facade {
    DetailsComponent getDetailsComponent();
  }

  private class MyWrapper extends Wrapper implements NullableComponent {
    public MyWrapper(final JComponent c) {
      super(c == null || NullableComponent.Check.isNull(c) ? myEmptyContentLabel : c);
    }

    @Override
    public boolean isNull() {
      return getTargetComponent() == myEmptyContentLabel;
    }
  }


}
