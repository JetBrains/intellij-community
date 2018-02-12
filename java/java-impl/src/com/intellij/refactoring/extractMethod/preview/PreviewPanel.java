// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.ide.actions.exclusion.ExclusionHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.Navigatable;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.List;
import java.util.Optional;

/**
 * @author Pavel.Dolgov
 */
class PreviewPanel extends BorderLayoutPanel implements Disposable, DataProvider {
  private final Project myProject;
  private final PreviewTree myTree;
  private final ExclusionHandler<DefaultMutableTreeNode> myExclusionHandler;
  private Content myContent;
  private final PreviewDiffPanel myDiffPanel;

  public PreviewPanel(ExtractMethodProcessor processor) {
    myProject = processor.getProject();
    myTree = new PreviewTree(processor);
    JScrollPane treePane = ScrollPaneFactory.createScrollPane(myTree.getComponent());
    treePane.putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.RIGHT);

    myDiffPanel = new PreviewDiffPanel(processor);
    myTree.addTreeListener(myDiffPanel);

    BorderLayoutPanel leftPanel = new BorderLayoutPanel();
    leftPanel.addToCenter(treePane);
    leftPanel.addToBottom(new ButtonsPanel(myProject));

    JBSplitter splitter = new JBSplitter(false);
    splitter.setProportion(0.3f);
    splitter.setFirstComponent(leftPanel);
    splitter.setSecondComponent(myDiffPanel);
    addToCenter(splitter);

    myExclusionHandler = new PreviewExclusionHandler(this);

    Disposer.register(processor.getProject(), this);
    Disposer.register(this, myTree);
    Disposer.register(this, myDiffPanel);
  }

  @Nullable
  @Override
  public Object getData(String dataId) {
    if (ExclusionHandler.EXCLUSION_HANDLER.is(dataId)) {
      return myExclusionHandler;
    }
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      List<FragmentNode> selectedNodes = myTree.getSelectedNodes();
      if (selectedNodes.size() == 1) {
        return Optional.ofNullable(selectedNodes.get(0))
                       .map(FragmentNode::getNavigatable)
                       .map(n -> new Navigatable[]{n})
                       .orElse(null);
      }
    }
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      List<FragmentNode> selectedNodes = myTree.getSelectedNodes();
      if (!selectedNodes.isEmpty()) {
        return StreamEx.of(selectedNodes)
                       .map(FragmentNode::getNavigatable)
                       .nonNull()
                       .toArray(Navigatable[]::new);
      }
    }
    return null;
  }

  public void setContent(Content content) {
    myContent = content;
    content.setDisposer(this);
  }

  public void initLater() {
    myDiffPanel.initLater(myTree.getAllDuplicates(), () -> myTree.onSelectionUpdate());
  }

  @Override
  public void dispose() {
  }

  private void close() {
    if (myContent != null) {
      ExtractMethodPreviewManager.getInstance(myProject).closeContent(myContent);
    }
  }

  private void doRefactor() {
    myDiffPanel.doExtract(myTree.getEnabledDuplicates());
    close();
  }

  void onTreeUpdated() {
    myTree.getComponent().repaint();
  }

  private class ButtonsPanel extends JPanel {
    private final JButton myRefactorButton;
    private final JButton myCancelButton;

    public ButtonsPanel(Project project) {
      super(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));

      myRefactorButton = new JButton("Do Refactor");
      myRefactorButton.addActionListener(e -> doRefactor());
      add(myRefactorButton);

      myCancelButton = new JButton("Cancel");
      myCancelButton.addActionListener(e -> close());
      add(myCancelButton);

      project.getMessageBus().connect(PreviewPanel.this).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
        @Override
        public void enteredDumbMode() {
          update(true);
        }

        @Override
        public void exitDumbMode() {
          update(false);
        }
      });
    }

    void update(boolean isDumb) {
      myRefactorButton.setEnabled(!isDumb);
      myCancelButton.setEnabled(!isDumb);
    }
  }
}
