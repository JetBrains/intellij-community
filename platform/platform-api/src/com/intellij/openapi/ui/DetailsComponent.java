// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;

public class DetailsComponent {

  private final JPanel myComponent;

  private JComponent myContent;


  private final Banner myBannerLabel;

  private final JLabel myEmptyContentLabel;
  private final NonOpaquePanel myBanner;

  private String[] myBannerText;
  private boolean myDetailsEnabled;
  private String[] myPrefix;
  private String[] myText;

  private final Wrapper myContentGutter = new Wrapper();

  private boolean myPaintBorder;

  public DetailsComponent() {
    this(true, true);
  }

  public DetailsComponent(boolean detailsEnabled, boolean paintBorder) {
    myDetailsEnabled = detailsEnabled;
    myPaintBorder = paintBorder;
    myComponent = new NonOpaquePanel(new BorderLayout());

    myComponent.setOpaque(false);
    myContentGutter.setOpaque(false);
    myContentGutter.setBorder(null);

    myBanner = new NonOpaquePanel(new BorderLayout());
    myBannerLabel = new Banner();

    if (myDetailsEnabled) {
      myBanner.add(myBannerLabel, BorderLayout.CENTER);
    }

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

  public void forProject(Project project) {
    myBannerLabel.forProject(project);
  }

  public void setPrefix(String @Nullable ... prefix) {
    myPrefix = prefix;
    if (myText != null) {
      setText(myText);
    }
  }

  public void setText(String @Nullable ... text) {
    myText = text;
    update();
  }

  public void update() {
    ArrayList<String> strings = new ArrayList<>();
    if (myPrefix != null) {
      ContainerUtil.addAll(strings, myPrefix);
    }

    if (myText != null) {
      ContainerUtil.addAll(strings, myText);
    }

    myBannerText = ArrayUtilRt.toStringArray(strings);

    updateBanner();
  }

  private void updateBanner() {
    myBannerLabel.setText(NullableComponent.Check.isNull(myContent) || myBannerText == null ? ArrayUtilRt.EMPTY_STRING_ARRAY : myBannerText);

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
    MyWrapper(final JComponent c) {
      super(c == null || NullableComponent.Check.isNull(c) ? myEmptyContentLabel : c);
    }

    @Override
    public boolean isNull() {
      return getTargetComponent() == myEmptyContentLabel;
    }
  }


}
