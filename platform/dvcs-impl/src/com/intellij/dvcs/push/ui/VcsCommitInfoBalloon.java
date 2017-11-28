/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.dvcs.push.ui;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.tree.TreePath;
import java.awt.*;

public class VcsCommitInfoBalloon {
  private static final String EMPTY_COMMIT_INFO = "<i style='color:gray;'>No commit information found</i>";

  @NotNull private final JTree myTree;
  @NotNull private final Wrapper myWrapper;
  @Nullable private JBPopup myBalloon;
  @NotNull private final JEditorPane myEditorPane;
  @NotNull private final ComponentPopupBuilder myPopupBuilder;

  public VcsCommitInfoBalloon(@NotNull JTree tree) {
    myTree = tree;
    myEditorPane = new JEditorPane(UIUtil.HTML_MIME, "");
    myEditorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    myEditorPane.setEditable(false);
    myEditorPane.setBackground(HintUtil.getInformationColor());
    myEditorPane.setFont(VcsHistoryUtil.getCommitDetailsFont());
    myEditorPane.setBorder(HintUtil.createHintBorder());
    Border margin = JBUI.Borders.empty(3, 3, 3, 3);
    myEditorPane.setBorder(new CompoundBorder(myEditorPane.getBorder(), margin));
    myEditorPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        BrowserUtil.browse(e.getURL());
      }
    });
    myWrapper = new Wrapper(myEditorPane);
    myPopupBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(myWrapper, null);
    myPopupBuilder.setCancelOnClickOutside(true).setResizable(true).setMovable(true).setRequestFocus(false)
      .setMinSize(new Dimension(80, 30));
  }

  public void updateCommitDetails() {
    if (myBalloon != null && myBalloon.isVisible()) {
      TreePath[] selectionPaths = myTree.getSelectionPaths();
      if (selectionPaths == null || selectionPaths.length != 1) {
        myBalloon.cancel();
      }
      else {
        Object node = selectionPaths[0].getLastPathComponent();
        myEditorPane.setText(
          XmlStringUtil.wrapInHtml(node instanceof TooltipNode ? ((TooltipNode)node).getTooltip().replaceAll("\n", "<br>") :
                                   EMPTY_COMMIT_INFO));
        //workaround: fix initial size for JEditorPane
        RepaintManager rp = RepaintManager.currentManager(myEditorPane);
        rp.markCompletelyDirty(myEditorPane);
        rp.validateInvalidComponents();
        rp.paintDirtyRegions();
        //
        myBalloon.setSize(myWrapper.getPreferredSize());
        myBalloon.setLocation(calculateBestPopupLocation());
      }
    }
  }

  @NotNull
  private Point calculateBestPopupLocation() {
    Point defaultLocation = myTree.getLocationOnScreen();
    TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath == null) return defaultLocation;
    Rectangle rectangle = myTree.getPathBounds(selectionPath);
    if (rectangle == null) return defaultLocation;
    Point location = rectangle.getLocation();
    SwingUtilities.convertPointToScreen(location, myTree);
    return new Point(location.x, location.y + rectangle.height);
  }

  private void createNewCommitInfoBalloon() {
    myBalloon = myPopupBuilder.createPopup();
    myBalloon.setSize(myEditorPane.getPreferredSize());
  }

  public void showCommitDetails() {
    if (myBalloon == null || !myBalloon.isVisible()) {
      createNewCommitInfoBalloon();
      myBalloon.show(new RelativePoint(calculateBestPopupLocation()));
    }
    updateCommitDetails();
  }
}
