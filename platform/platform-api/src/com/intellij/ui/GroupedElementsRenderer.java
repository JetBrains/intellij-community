// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.internal.inspector.PropertyBean;
import com.intellij.internal.inspector.UiInspectorContextProvider;
import com.intellij.internal.inspector.UiInspectorUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.UpdateScaleHelper;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.util.ArrayList;

public abstract class GroupedElementsRenderer implements Accessible {
  protected SeparatorWithText mySeparatorComponent = createSeparator();

  protected abstract JComponent createItemComponent();

  /**
   * @deprecated Use {@link #getItemComponent()} getter instead, the field will be hidden
   */
  @Deprecated
  protected JComponent myComponent;
  protected MyComponent myRendererComponent;
  private UpdateScaleHelper updateScaleHelper;

  protected ErrorLabel myTextLabel;

  public GroupedElementsRenderer() {
    updateScaleHelper = new UpdateScaleHelper();
    myRendererComponent = new MyComponent();

    myComponent = createItemComponent();

    layout();
  }

  protected abstract void layout();

  public JComponent getItemComponent() {
    return myComponent;
  }

  protected SeparatorWithText createSeparator() {
    return new SeparatorWithText();
  }

  protected final JComponent configureComponent(@NlsContexts.ListItem String text, @NlsContexts.Tooltip String tooltip, Icon icon, Icon disabledIcon, boolean isSelected, boolean hasSeparatorAbove, @NlsContexts.Separator String separatorTextAbove, int preferredForcedWidth) {
    mySeparatorComponent.setVisible(hasSeparatorAbove);
    mySeparatorComponent.setCaption(separatorTextAbove);
    mySeparatorComponent.setMinimumWidth(preferredForcedWidth);

    myTextLabel.setText(text);
    myRendererComponent.setToolTipText(tooltip);
    AccessibleContextUtil.setName(myRendererComponent, myTextLabel);
    AccessibleContextUtil.setDescription(myRendererComponent, myTextLabel);

    setComponentIcon(icon, disabledIcon);
    updateSelection(isSelected, myComponent, myTextLabel);
    myRendererComponent.setPreferredWidth(preferredForcedWidth);

    updateScaleHelper.saveScaleAndUpdateUIIfChanged(myRendererComponent);

    return myRendererComponent;
  }

  protected void updateSelection(boolean isSelected, JComponent component, JComponent innerComponent) {
    if (!ExperimentalUI.isNewUI()) {
      setSelected(component, isSelected);
    } else {
      UIUtil.setNotOpaqueRecursively(component);
    }
    setSelected(innerComponent, isSelected);
  }

  protected void setComponentIcon(Icon icon, Icon disabledIcon) {
    myTextLabel.setIcon(icon);
    myTextLabel.setDisabledIcon(disabledIcon);
    myTextLabel.setIconTextGap(JBUI.CurrentTheme.ActionsList.elementIconGap());
  }

  protected final void setSelected(JComponent aComponent) {
    setSelected(aComponent, true);
  }

  protected final void setDeselected(JComponent aComponent) {
    setSelected(aComponent, false);
  }

  protected final void setSelected(JComponent aComponent, boolean selected) {
    UIUtil.setBackgroundRecursively(aComponent, selected ? getSelectionBackground() : getBackground());
    setForegroundSelected(aComponent, selected);
  }

  protected final void setForegroundSelected(JComponent aComponent, boolean selected) {
    aComponent.setForeground(selected ? getSelectionForeground() : getForeground());
  }

  protected void setSeparatorFont(Font font) {
    mySeparatorComponent.setFont(font);
  }

  protected abstract Color getSelectionBackground();

  protected abstract Color getSelectionForeground();

  protected abstract Color getBackground();

  protected abstract Color getForeground();

  protected Border getDefaultItemComponentBorder() {
    return getBorder();
  }

  private static Border getBorder() {
    return new EmptyBorder(JBUI.CurrentTheme.ActionsList.cellPadding());
  }

  public abstract static class List extends GroupedElementsRenderer {
    @Override
    protected void layout() {
      myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH);

      JComponent centerComponent = new NonOpaquePanel(myComponent) {
        @Override
        public Dimension getPreferredSize() {
          return UIUtil.updateListRowHeight(super.getPreferredSize());
        }
      };

      myRendererComponent.add(centerComponent, BorderLayout.CENTER);
    }

    @Override
    protected final Color getSelectionBackground() {
      return UIUtil.getListSelectionBackground(true);
    }

    @Override
    protected final Color getSelectionForeground() {
      return NamedColorUtil.getListSelectionForeground(true);
    }

    @Override
    protected Color getBackground() {
      return UIUtil.getListBackground();
    }

    @Override
    protected Color getForeground() {
      return UIUtil.getListForeground();
    }
  }

  public abstract static class Tree extends GroupedElementsRenderer implements TreeCellRenderer {

    @Override
    protected void layout() {
      myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH);
      myRendererComponent.add(myComponent, BorderLayout.WEST);
    }

    @Override
    protected Color getSelectionBackground() {
      return UIUtil.getTreeSelectionBackground();
    }

    @Override
    protected Color getSelectionForeground() {
      return UIUtil.getTreeSelectionForeground();
    }

    @Override
    protected Color getBackground() {
      return UIUtil.getTreeBackground();
    }

    @Override
    protected Color getForeground() {
      return UIUtil.getTreeForeground();
    }
  }

  public class MyComponent extends OpaquePanel implements UiInspectorContextProvider {

    private int myPrefWidth = -1;
    private final @NotNull GroupedElementsRenderer renderer;

    public MyComponent() {
      super(new BorderLayout(), GroupedElementsRenderer.this.getBackground());
      renderer = GroupedElementsRenderer.this;
    }

    public void setPreferredWidth(final int minWidth) {
      myPrefWidth = minWidth;
    }

    @Override
    public Dimension getPreferredSize() {
      final Dimension size = super.getPreferredSize();
      size.width = myPrefWidth == -1 ? size.width : myPrefWidth;
      return size;
    }

    @ApiStatus.Internal
    public @NotNull SeparatorWithText getSeparator() {
      return mySeparatorComponent;
    }

    @ApiStatus.Internal
    public @NotNull GroupedElementsRenderer getRenderer() {
      return renderer;
    }

    @Override
    public java.util.@NotNull List<PropertyBean> getUiInspectorContext() {
      java.util.List<PropertyBean> result = new ArrayList<>();
      result.add(new PropertyBean("Renderer Delegate", renderer));
      result.add(new PropertyBean("Renderer Delegate Class", UiInspectorUtil.getClassPresentation(renderer)));
      return result;
    }
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    return myRendererComponent.getAccessibleContext();
  }
}
