// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.errorTreeView;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CustomizeColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import com.intellij.util.ui.MutableErrorTreeView;

import javax.swing.*;

public class HotfixGroupElement extends GroupingElement {

  private final Consumer<? super HotfixGate> myHotfix;
  private final String myFixDescription;
  private final MutableErrorTreeView myView;
  private boolean myInProgress;
  private final CustomizeColoredTreeCellRenderer myLeftTreeCellRenderer;
  private final CustomizeColoredTreeCellRenderer myRightTreeCellRenderer;

  public HotfixGroupElement(final String name, final Object data, final VirtualFile file, final Consumer<? super HotfixGate> hotfix,
                            final String fixDescription, final MutableErrorTreeView view) {
    super(name, data, file);
    myHotfix = hotfix;
    myFixDescription = fixDescription;
    myView = view;
    myLeftTreeCellRenderer = new CustomizeColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(SimpleColoredComponent renderer,
                                        JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        renderer.setIcon(AllIcons.General.Error);

        final String[] text = getText();
        final String errorText = ((text != null) && (text.length > 0)) ? text[0] : "";
        renderer.append("Error: " + errorText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    };
    myRightTreeCellRenderer = new MyRightRenderer();
  }

  private class MyRightRenderer extends CustomizeColoredTreeCellRenderer {
    private final HotfixGroupElement.MyRunner myRunner;

    MyRightRenderer() {
      myRunner = new MyRunner();
    }

    @Override
    public void customizeCellRenderer(SimpleColoredComponent renderer,
                                      JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      renderer.append(" ");
      if (myInProgress) {
        renderer.append("fixing...", SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
      } else {
        renderer.append("Fix: " + myFixDescription, SimpleTextAttributes.LINK_BOLD_ATTRIBUTES, myRunner);
      }
    }

    @Override
    public Object getTag() {
      return myRunner;
    }
  }

  // we can inherit from HaveTooltip here
  @Override
  public CustomizeColoredTreeCellRenderer getLeftSelfRenderer() {
    return myLeftTreeCellRenderer;
  }

  @Override
  public CustomizeColoredTreeCellRenderer getRightSelfRenderer() {
    return myRightTreeCellRenderer;
  }

  private final class MyRunner implements Runnable {
    private MyRunner() {
    }

    // todo name can be an ID
    @Override
    public void run() {
      myInProgress = true;
      myView.reload();
      final String name = getName();
      myHotfix.consume(new HotfixGate(name, myView));
    }
  }
}
