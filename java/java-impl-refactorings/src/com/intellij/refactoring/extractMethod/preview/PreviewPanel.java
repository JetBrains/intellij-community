// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.exclusion.ExclusionHandler;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.PsiDocumentManagerEx;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.ui.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.usages.impl.UsageModelTracker;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.List;
import java.util.Optional;

class PreviewPanel extends BorderLayoutPanel implements Disposable, UiDataProvider {
  private final Project myProject;
  private final PreviewTree myTree;
  private final ExclusionHandler<DefaultMutableTreeNode> myExclusionHandler;
  private final ButtonsPanel myButtonsPanel;
  private Content myContent;
  private final PreviewDiffPanel myDiffPanel;
  private final Alarm myUpdateAlarm = new Alarm();

  PreviewPanel(ExtractMethodProcessor processor) {
    myProject = processor.getProject();
    myTree = new PreviewTree(processor);
    JScrollPane treePane = ScrollPaneFactory.createScrollPane(myTree.getComponent());
    treePane.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

    myDiffPanel = new PreviewDiffPanel(processor, myTree);
    myTree.addTreeListener(myDiffPanel);

    BorderLayoutPanel leftPanel = new BorderLayoutPanel();
    leftPanel.addToCenter(treePane);
    myButtonsPanel = new ButtonsPanel(myProject);
    leftPanel.addToBottom(myButtonsPanel);

    JBSplitter splitter = new OnePixelSplitter(false);
    splitter.setProportion(0.3f);
    splitter.setFirstComponent(leftPanel);
    splitter.setSecondComponent(myDiffPanel);
    addToCenter(splitter);

    myExclusionHandler = new PreviewExclusionHandler(this);

    UsageModelTracker usageModelTracker = new UsageModelTracker(myProject);
    Disposer.register(this, usageModelTracker);
    usageModelTracker.addListener(isPropertyChange -> updateLater(), this);

    Disposer.register(this, myTree);
    Disposer.register(this, myDiffPanel);
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    List<FragmentNode> selectedNodes = myTree.getSelectedNodes();
    sink.set(ExclusionHandler.EXCLUSION_HANDLER, myExclusionHandler);
    sink.lazy(CommonDataKeys.NAVIGATABLE, () -> {
      if (selectedNodes.size() != 1) return null;
      return Optional.ofNullable(selectedNodes.get(0))
        .map(FragmentNode::getNavigatable)
        .orElse(null);
    });
    sink.lazy(CommonDataKeys.NAVIGATABLE_ARRAY, () -> {
      if (selectedNodes.isEmpty()) return null;
      return StreamEx.of(selectedNodes)
        .map(FragmentNode::getNavigatable)
        .nonNull()
        .toArray(Navigatable[]::new);
    });
  }

  public void setContent(Content content) {
    myContent = content;
    content.setDisposer(this);
  }

  public void initLater() {
    myDiffPanel.initLater();
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
    if (myTree.isValid()) {
      myDiffPanel.doExtract();
      close();
      return;
    }
    if (Messages.showYesNoDialog(myProject,
                                 JavaRefactoringBundle.message("project.files.have.been.changed"),
                                 JavaRefactoringBundle.message("re.run.refactoring"), null) == Messages.YES) {
      close();
      myDiffPanel.tryExtractAgain();
    }
  }

  private void rerunRefactoring() {
    close();
    myDiffPanel.tryExtractAgain();
  }

  void onTreeUpdated() {
    myTree.repaint();
    myDiffPanel.updateLater();
  }

  private void updateLater() {
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(() -> {
      if (myProject.isDisposed()) return;
      PsiDocumentManagerEx documentManager = (PsiDocumentManagerEx)PsiDocumentManager.getInstance(myProject);
      documentManager.cancelAndRunWhenAllCommitted("ExtractMethodPreview", this::updateImmediately);
    }, 300);
  }

  private void updateImmediately() {
    ThreadingAssertions.assertEventDispatchThread();
    if (myProject.isDisposed()) return;

    boolean isModified = myDiffPanel.isModified();
    if (myButtonsPanel.updateButtons(isModified)) {
      myTree.setValid(!isModified);
    }
  }

  private class ButtonsPanel extends JPanel {
    private final JButton myRefactorButton;
    private final JButton myRerunButton;
    private final JButton myCancelButton;
    private final Project myProject;
    private boolean myModified; // Accessed in EDT

    ButtonsPanel(@NotNull Project project) {
      super(new FlowLayout(FlowLayout.LEFT, JBUIScale.scale(8), 0));
      myProject = project;

      myRefactorButton = new JButton(JavaRefactoringBundle.message("refactoring.extract.method.preview.button.refactor"));
      DialogUtil.registerMnemonic(myRefactorButton);
      myRefactorButton.addActionListener(e -> doRefactor());
      add(myRefactorButton);

      myRerunButton = new JButton(JavaRefactoringBundle.message("refactoring.extract.method.preview.button.rerun"));
      DialogUtil.registerMnemonic(myRefactorButton);
      myRerunButton.addActionListener(e -> rerunRefactoring());
      add(myRerunButton);

      myCancelButton = new JButton(IdeBundle.message("button.cancel"));
      DialogUtil.registerMnemonic(myRefactorButton);
      myCancelButton.addActionListener(e -> close());
      add(myCancelButton);

      updateButtonsImpl(false, false);

      project.getMessageBus().connect(PreviewPanel.this).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
        @Override
        public void enteredDumbMode() {
          updateButtonsLater(true);
        }

        @Override
        public void exitDumbMode() {
          updateButtonsLater(false);
        }
      });
    }

    boolean updateButtons(boolean isModified) {
      if (myModified == isModified) {
        return false;
      }
      myModified = isModified;
      updateButtonsImpl(DumbService.isDumb(myProject), isModified);
      return true;
    }

    void updateButtonsLater(boolean isDumb) {
      ApplicationManager.getApplication().invokeLater(() -> updateButtonsImpl(isDumb, myModified));
    }

    private void updateButtonsImpl(boolean isDumb, boolean isModified) {
      myRefactorButton.setEnabled(!isDumb && !isModified);
      myRefactorButton.setVisible(!isModified);
      myRerunButton.setEnabled(!isDumb && isModified);
      myRerunButton.setVisible(isModified);
    }
  }
}
