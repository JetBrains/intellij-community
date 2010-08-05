/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.EdgeBorder;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfoToUsageConverter;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.UsageViewPresentation;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class ImplementationViewComponent extends JPanel {
  private final PsiElement[] myElements;
  private int myIndex;

  private final Editor myEditor;
  private final JPanel myViewingPanel;
  private final JLabel myLocationLabel;
  private final JLabel myCountLabel;
  private final CardLayout myBinarySwitch;
  private final JPanel myBinaryPanel;
  private JComboBox myFileChooser;
  private FileEditor myNonTextEditor;
  private FileEditorProvider myCurrentNonTextEditorProvider;
  private JBPopup myHint;
  private String myTitle;
  @NonNls private static final String TEXT_PAGE_KEY = "Text";
  @NonNls private static final String BINARY_PAGE_KEY = "Binary";
  private final ActionToolbar myToolbar;
  private static final Icon FIND_ICON = IconLoader.getIcon("/actions/find.png");

  public void setHint(final JBPopup hint, final String title) {
    myHint = hint;
    myTitle = title;
  }

  public boolean hasElementsToShow() {
    return myElements.length > 0;
  }

  private static class FileDescriptor {
    public final VirtualFile myFile;

    public FileDescriptor(VirtualFile file) {
      myFile = file;
    }
  }

  public ImplementationViewComponent(PsiElement[] elements, final int index) {
    super(new BorderLayout());
    List<PsiElement> candidates = new ArrayList<PsiElement>(elements.length);
    List<FileDescriptor> files = new ArrayList<FileDescriptor>(elements.length);
    for (PsiElement element : elements) {
      PsiFile file = getContainingFile(element);
      if (file == null) continue;
      files.add(new FileDescriptor(file.getVirtualFile()));
      candidates.add(element.getNavigationElement());
    }
    myElements = candidates.toArray(new PsiElement[candidates.size()]);
    if (myElements.length == 0) {
      myToolbar = null;
      myEditor = null;
      myViewingPanel = null;
      myLocationLabel = null;
      myCountLabel = null;
      myBinarySwitch = null;
      myBinaryPanel = null;
      return;
    }
    myIndex = index < myElements.length ? index : 0;

    final Project project = elements[myIndex].getProject();
    EditorFactory factory = EditorFactory.getInstance();
    Document doc = factory.createDocument("");
    doc.setReadOnly(true);
    myEditor = factory.createEditor(doc, project);
    PsiFile psiFile = getContainingFile(myElements[myIndex]);

    VirtualFile virtualFile = psiFile.getVirtualFile();
    EditorHighlighter highlighter;
    if (virtualFile != null)
      highlighter = HighlighterFactory.createHighlighter(project, virtualFile);
    else {
      String fileName = psiFile.getName();  // some artificial psi file, lets do best we can
      highlighter = HighlighterFactory.createHighlighter(project, fileName);
    }

    ((EditorEx)myEditor).setHighlighter(highlighter);
    ((EditorEx)myEditor).setBackgroundColor(EditorFragmentComponent.getBackgroundColor(myEditor));

    myEditor.getSettings().setAdditionalLinesCount(1);
    myEditor.getSettings().setAdditionalColumnsCount(1);
    myEditor.getSettings().setLineMarkerAreaShown(false);
    myEditor.getSettings().setIndentGuidesShown(false);
    myEditor.getSettings().setLineNumbersShown(false);
    myEditor.getSettings().setFoldingOutlineShown(false);

    myBinarySwitch = new CardLayout();
    myViewingPanel = new JPanel(myBinarySwitch);
    final Border lineBorder = new EdgeBorder(EdgeBorder.EDGE_TOP);
    final Border emptyBorder = BorderFactory.createEmptyBorder(0, 2, 2, 2);
    final Border compoundBorder = BorderFactory.createCompoundBorder(emptyBorder, lineBorder);
    myViewingPanel.setBorder(compoundBorder);
    myViewingPanel.add(myEditor.getComponent(), TEXT_PAGE_KEY);

    myBinaryPanel = new JPanel(new BorderLayout());
    myViewingPanel.add(myBinaryPanel, BINARY_PAGE_KEY);

    add(myViewingPanel, BorderLayout.CENTER);

    myToolbar = createToolbar();
    myLocationLabel = new JLabel();
    myCountLabel = new JLabel();

    JPanel header = new JPanel(new BorderLayout());
    header.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    JPanel toolbarPanel = new JPanel(new FlowLayout());
    toolbarPanel.add(myToolbar.getComponent());

    if (myElements.length > 1) {
      myFileChooser = new JComboBox(files.toArray(new FileDescriptor[files.size()]));
      myFileChooser.setRenderer(new DefaultListCellRenderer() {
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          super.getListCellRendererComponent(list, null, index, isSelected, cellHasFocus);
          VirtualFile file = ((FileDescriptor)value).myFile;
          setIcon(file.getIcon());
          setForeground(FileStatusManager.getInstance(project).getStatus(file).getColor());
          setText(file.getPresentableName());
          return this;
        }
      });

      myFileChooser.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          int index = myFileChooser.getSelectedIndex();
          if (myIndex != index) {
            myIndex = index;
            updateControls();
          }
        }
      });

      toolbarPanel.add(myFileChooser);
      toolbarPanel.add(myCountLabel);
    }
    else {
      final JLabel label = new JLabel();
      VirtualFile file = psiFile.getVirtualFile();
      if (file != null) {
        label.setIcon(file.getIcon());
        label.setForeground(FileStatusManager.getInstance(project).getStatus(file).getColor());
        label.setText(file.getPresentableName());
        label.setBorder(new CompoundBorder(IdeBorderFactory.createRoundedBorder(), IdeBorderFactory.createEmptyBorder(0, 0, 0, 5)));
      }
      toolbarPanel.add(label);
    }


    header.add(toolbarPanel, BorderLayout.WEST);
    header.add(myLocationLabel, BorderLayout.EAST);

    add(header, BorderLayout.NORTH);
    setPreferredSize(new Dimension(600, 400));

    updateControls();
  }

  public JComponent getPrefferedFocusableComponent() {
    return myFileChooser != null ? myFileChooser : myViewingPanel;
  }

  private void updateControls() {
    updateLabels();
    updateCombo();
    updateEditorText();
    myToolbar.updateActionsImmediately();
  }

  private void updateCombo() {
    if (myFileChooser != null) {
      myFileChooser.setSelectedIndex(myIndex);
    }
  }

  private void updateEditorText() {
    disposeNonTextEditor();

    final PsiElement elt = myElements[myIndex];
    Project project = elt.getProject();
    PsiFile psiFile = getContainingFile(elt);
    final VirtualFile vFile = psiFile.getVirtualFile();
    if (vFile == null) return;
    final FileEditorProvider[] providers = FileEditorProviderManager.getInstance().getProviders(project, vFile);
    for (FileEditorProvider provider : providers) {
      if (provider instanceof TextEditorProvider) {
        updateTextElement(elt);
        myBinarySwitch.show(myViewingPanel, TEXT_PAGE_KEY);
        break;
      }
      else if (provider.accept(project, vFile)) {
        myCurrentNonTextEditorProvider = provider;
        myNonTextEditor = myCurrentNonTextEditorProvider.createEditor(project, vFile);
        myBinaryPanel.removeAll();
        myBinaryPanel.add(myNonTextEditor.getComponent());
        myBinarySwitch.show(myViewingPanel, BINARY_PAGE_KEY);
        break;
      }
    }
  }

  private void disposeNonTextEditor() {
    if (myNonTextEditor != null) {
      myCurrentNonTextEditorProvider.disposeEditor(myNonTextEditor);
      myNonTextEditor = null;
      myCurrentNonTextEditorProvider = null;
    }
  }

  private void updateTextElement(final PsiElement elt) {
    Project project = elt.getProject();
    PsiFile psiFile = getContainingFile(elt);

    final Document doc = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (doc == null) return;

    final ImplementationTextSelectioner implementationTextSelectioner =
      LanguageImplementationTextSelectioner.INSTANCE.forLanguage(elt.getLanguage());
    int start = implementationTextSelectioner.getTextStartOffset(elt);
    final int end = implementationTextSelectioner.getTextEndOffset(elt);

    final int lineStart = doc.getLineStartOffset(doc.getLineNumber(start));
    final int lineEnd = end < doc.getTextLength() ? doc.getLineEndOffset(doc.getLineNumber(end)) : end;

    CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            Document fragmentDoc = myEditor.getDocument();
            fragmentDoc.setReadOnly(false);
            fragmentDoc.replaceString(0, fragmentDoc.getTextLength(), doc.getCharsSequence().subSequence(lineStart, lineEnd).toString());
            fragmentDoc.setReadOnly(true);
            myEditor.getCaretModel().moveToOffset(0);
            myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          }
        });
      }
    });
  }

  private static PsiFile getContainingFile(final PsiElement elt) {
    PsiFile psiFile = elt.getContainingFile();
    if (psiFile == null) return null;
    return psiFile.getOriginalFile();
  }

  public void removeNotify() {
    super.removeNotify();
    EditorFactory.getInstance().releaseEditor(myEditor);
    disposeNonTextEditor();
  }

  private void updateLabels() {
    //TODO: Move from JavaDoc to somewhere more appropriate place.
    ElementLocationUtil.customizeElementLabel(myElements[myIndex], myLocationLabel);
    //noinspection AutoBoxing
    myCountLabel.setText(CodeInsightBundle.message("n.of.m", myIndex + 1, myElements.length));
  }

  private ActionToolbar createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();

    BackAction back = new BackAction();
    back.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)), this);
    group.add(back);

    ForwardAction forward = new ForwardAction();
    forward.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)), this);
    group.add(forward);

    EditSourceActionBase edit = new EditSourceAction();
    edit.registerCustomShortcutSet(new CompositeShortcutSet(CommonShortcuts.getEditSource(), CommonShortcuts.ENTER), this);
    group.add(edit);

    edit = new ShowSourceAction();
    edit.registerCustomShortcutSet(new CompositeShortcutSet(CommonShortcuts.getViewSource(), CommonShortcuts.CTRL_ENTER), this);
    group.add(edit);

    final ShowFindUsagesAction findUsagesAction = new ShowFindUsagesAction();
    findUsagesAction.registerCustomShortcutSet(new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_FIND_USAGES)), this);
    group.add(findUsagesAction);

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
  }

  private void goBack() {
    myIndex--;
    updateControls();
  }

  private void goForward() {
    myIndex++;
    updateControls();
  }

  private class BackAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    public BackAction() {
      super(CodeInsightBundle.message("quick.definition.back"), null, IconLoader.getIcon("/actions/back.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      goBack();
    }


    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myIndex > 0);
    }
  }

  private class ForwardAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    public ForwardAction() {
      super(CodeInsightBundle.message("quick.definition.forward"), null, IconLoader.getIcon("/actions/forward.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      goForward();
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myIndex < myElements.length - 1);
    }
  }

  private class EditSourceAction extends EditSourceActionBase {
    public EditSourceAction() {
      super(true, IconLoader.getIcon("/actions/editSource.png"), CodeInsightBundle.message("quick.definition.edit.source"));
    }

    @Override public void actionPerformed(AnActionEvent e) {
      super.actionPerformed(e);
      if (myHint.isVisible()) {
        myHint.cancel();
      }
    }
  }

  private class ShowSourceAction extends EditSourceActionBase implements HintManagerImpl.ActionToIgnore {
    public ShowSourceAction() {
      super(false, IconLoader.getIcon("/actions/showSource.png"), CodeInsightBundle.message("quick.definition.show.source"));
    }
  }

  private class EditSourceActionBase extends AnAction {
    private final boolean myFocusEditor;

    public EditSourceActionBase(boolean focusEditor, Icon icon, String text) {
      super(text, null, icon);
      myFocusEditor = focusEditor;
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myFileChooser == null || !myFileChooser.isPopupVisible());
    }

    public void actionPerformed(AnActionEvent e) {
      PsiElement element = myElements[myIndex];
      PsiElement navigationElement = element.getNavigationElement();
      PsiFile file = getContainingFile(navigationElement);
      if (file == null) return;
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile == null) return;
      Project project = element.getProject();
      FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile, navigationElement.getTextOffset());
      fileEditorManager.openTextEditor(descriptor, myFocusEditor);
    }
  }

  private class ShowFindUsagesAction extends AnAction {
    private static final String ACTION_NAME = "Show in usage view";

    public ShowFindUsagesAction() {
      super(ACTION_NAME, ACTION_NAME, FIND_ICON);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      final UsageViewPresentation presentation = new UsageViewPresentation();
      presentation.setCodeUsagesString(myTitle);
      presentation.setTabName(myTitle);
      presentation.setTabText(myTitle);
      PsiElement[] elements = collectNonBinaryElements();
      final UsageInfo[] usages = new UsageInfo[elements.length];
      for (int i = 0; i < elements.length; i++) {
        usages[i] = new UsageInfo(elements[i]);
      }
      UsageViewManager.getInstance(myEditor.getProject()).showUsages(UsageTarget.EMPTY_ARRAY, UsageInfoToUsageConverter.convert(
        new UsageInfoToUsageConverter.TargetElementsDescriptor(elements), usages), presentation);
      if (myHint.isVisible()) {
        myHint.cancel();
      }
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setVisible(collectNonBinaryElements().length > 0);
    }
  }

  private PsiElement[] collectNonBinaryElements() {
    List<PsiElement> result = new ArrayList<PsiElement>();
    for (PsiElement element : myElements) {
      if (!(element instanceof PsiBinaryFile)) {
        result.add(element);
      }
    }
    return result.toArray(new PsiElement[result.size()]);
  }
}
