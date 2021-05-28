// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.find.FindUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.text.QuickDefinitionProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.list.LeftRightRenderer;
import com.intellij.usages.UsageView;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.PairFunction;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

public class ImplementationViewComponent extends JPanel {
  @NonNls private static final String TEXT_PAGE_KEY = "Text";
  @NonNls private static final String BINARY_PAGE_KEY = "Binary";
  private static final String IMPLEMENTATION_VIEW_PLACE = "ImplementationView";
  private final EditorFactory factory;
  private final Project project;

  private ImplementationViewElement[] myElements;
  private int myIndex;

  private EditorEx myEditor;
  private volatile boolean myEditorReleased;
  private final JPanel myViewingPanel;
  private final CardLayout myBinarySwitch;
  private final JPanel myBinaryPanel;
  private ComboBox<FileDescriptor> myFileChooser;
  private FileEditor myNonTextEditor;
  private FileEditorProvider myCurrentNonTextEditorProvider;
  private JBPopup myHint;
  private @NlsContexts.TabTitle String myTitle;
  private final ActionToolbar myToolbar;
  private JPanel mySingleEntryPanel;

  public void setHint(final JBPopup hint, @NotNull @NlsContexts.TabTitle String title) {
    myHint = hint;
    myTitle = title;
  }

  public boolean hasElementsToShow() {
    return myElements != null && myElements.length > 0;
  }

  private static class FileDescriptor {
    @NotNull public final VirtualFile myFile;
    public final ImplementationViewElement myElement;

    FileDescriptor(@NotNull VirtualFile file, ImplementationViewElement element) {
      myFile = file;
      myElement = element;
    }
  }

  public ImplementationViewComponent(Collection<ImplementationViewElement> elements,
                                   final int index) {
    this(elements, index, null);
  }

  public ImplementationViewComponent(Collection<ImplementationViewElement> elements, final int index, Consumer<ImplementationViewComponent> openUsageView) {
    super(new BorderLayout());

    project = elements.size() > 0 ? elements.iterator().next().getProject() : null;
    factory = EditorFactory.getInstance();
    Document doc = factory.createDocument("");
    doc.setReadOnly(true);
    myEditor = (EditorEx)factory.createEditor(doc, project, EditorKind.PREVIEW);
    tuneEditor();

    myBinarySwitch = new CardLayout();
    myViewingPanel = new JPanel(myBinarySwitch);
    myViewingPanel.add(myEditor.getComponent(), TEXT_PAGE_KEY);

    myBinaryPanel = new JPanel(new BorderLayout());
    myViewingPanel.add(myBinaryPanel, BINARY_PAGE_KEY);

    add(myViewingPanel, BorderLayout.CENTER);

    myToolbar = createToolbar(openUsageView);

    setPreferredSize(JBUI.size(600, 400));

    update(elements, (psiElements, fileDescriptors) -> {
      if (psiElements.length == 0) return false;
      myElements = psiElements;

      myIndex = index < myElements.length ? index : 0;
      VirtualFile virtualFile = myElements[myIndex].getContainingFile();

      tuneEditor(virtualFile);

      final JPanel toolbarPanel = new JPanel(new GridBagLayout());
      final GridBagConstraints gc =
        new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                               JBUI.insets(0), 0, 0);

      mySingleEntryPanel = new JPanel(new BorderLayout());
      toolbarPanel.add(mySingleEntryPanel, gc);

      myFileChooser = new ComboBox<>(fileDescriptors.toArray(new FileDescriptor[0]), 250);
      myFileChooser.setOpaque(false);
      myFileChooser.addActionListener(e -> {
        int index1 = myFileChooser.getSelectedIndex();
        if (myIndex != index1) {
          myIndex = index1;
          UIEventLogger.ImplementationViewComboBoxSelected.log(project);
          updateControls();
        }
      });
      toolbarPanel.add(myFileChooser, gc);

      if (myElements.length > 1) {
        mySingleEntryPanel.setVisible(false);
        updateRenderer(project);
      }
      else {
        myFileChooser.setVisible(false);

        if (virtualFile != null) {
          updateSingleEntryLabel(virtualFile);
        }
      }

      gc.fill = GridBagConstraints.NONE;
      gc.weightx = 0;

      JComponent component = myToolbar.getComponent();
      component.setBorder(null);
      toolbarPanel.add(component, gc);

      toolbarPanel.setBackground(UIUtil.getToolTipActionBackground());
      toolbarPanel.setBorder(JBUI.Borders.empty(3));
      toolbarPanel.setOpaque(false);
      add(toolbarPanel, BorderLayout.NORTH);

      updateControls();
      return true;
    });
  }

  private DefaultActionGroup createGearActionButton(Consumer<ImplementationViewComponent> openUsageView) {
    DefaultActionGroup gearActions = new DefaultActionGroup() {
      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setIcon(AllIcons.Actions.More);
        e.getPresentation().putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, Boolean.TRUE);
      }
    };
    gearActions.setPopup(true);
    EditSourceActionBase edit = new EditSourceAction();
    edit.registerCustomShortcutSet(new CompositeShortcutSet(CommonShortcuts.getEditSource(), CommonShortcuts.ENTER), this);
    gearActions.add(edit);
    if (openUsageView != null) {
      Icon icon = ToolWindowManager.getInstance(project).getLocationIcon(ToolWindowId.FIND, AllIcons.General.Pin_tab);
      gearActions.add(new AnAction(() -> IdeBundle.message("show.in.find.window.button.name"), icon) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          openUsageView.accept(ImplementationViewComponent.this);
          if (myHint.isVisible()) {
            myHint.cancel();
          }
        }
      });
    }
    return gearActions;
  }

  private  void updateSingleEntryLabel(VirtualFile virtualFile) {
    mySingleEntryPanel.removeAll();
    JLabel label = new JLabel(myElements[myIndex].getPresentableText(), getIconForFile(virtualFile, project), SwingConstants.LEFT);
    mySingleEntryPanel.add(label, BorderLayout.CENTER);
    label.setForeground(FileStatusManager.getInstance(project).getStatus(virtualFile).getColor());
    
    mySingleEntryPanel.add(new JLabel(myElements[myIndex].getLocationText(), myElements[myIndex].getLocationIcon(), SwingConstants.LEFT), BorderLayout.EAST);
    mySingleEntryPanel.setOpaque(false);
    mySingleEntryPanel.setVisible(true);
    mySingleEntryPanel.setBorder(JBUI.Borders.empty(4, 3));
  }

  private void tuneEditor(VirtualFile virtualFile) {
    if (virtualFile != null) {
      myEditor.setHighlighter(HighlighterFactory.createHighlighter(project, virtualFile));
    }
  }

  private void tuneEditor() {
    Color color = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.DOCUMENTATION_COLOR);
    if (color != null) {
      myEditor.setBackgroundColor(color);
    }
    final EditorSettings settings = myEditor.getSettings();
    settings.setAdditionalLinesCount(1);
    settings.setAdditionalColumnsCount(1);
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setLineNumbersShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setCaretRowShown(false);

    myEditor.setBorder(JBUI.Borders.empty(12, 6));
    myEditor.getScrollPane().setViewportBorder(JBScrollPane.createIndentBorder());
  }

  private void updateRenderer(final Project project) {
    myFileChooser.setRenderer(createRenderer(project));
  }

  private static ListCellRenderer<FileDescriptor> createRenderer(Project project) {
    return new LeftRightRenderer<>() {
      @NotNull
      @Override
      protected ListCellRenderer<FileDescriptor> getMainRenderer() {
        return new ColoredListCellRenderer<>() {
          @Override
          protected void customizeCellRenderer(@NotNull JList<? extends FileDescriptor> list,
                                               FileDescriptor value, int index, boolean selected, boolean hasFocus) {
            if (value != null) {
              ImplementationViewElement element = value.myElement;
              setIcon(getIconForFile(value.myFile, project));
              append(element.getPresentableText());
              String presentation = element.getContainerPresentation();
              if (presentation != null) {
                append("  ");
                append(StringUtil.trimStart(StringUtil.trimEnd(presentation, ")"), "("), SimpleTextAttributes.GRAYED_ATTRIBUTES);
              }
            }
          }
        };
      }

      @NotNull
      @Override
      protected ListCellRenderer<FileDescriptor> getRightRenderer() {
        return new SimpleListCellRenderer<>() {
          @Override
          public void customize(@NotNull JList<? extends FileDescriptor> list,
                                FileDescriptor value, int index, boolean selected, boolean hasFocus) {
            if (value != null) {
              setText(value.myElement.getLocationText());
              setIcon(value.myElement.getLocationIcon());
            }
          }
        };
      }
    };
  }

  @TestOnly
  public String[] getVisibleFiles() {
    final ComboBoxModel<FileDescriptor> model = myFileChooser.getModel();
    String[] result = new String[model.getSize()];
    for (int i = 0; i < model.getSize(); i++) {
      FileDescriptor o = model.getElementAt(i);
      result[i] = o.myElement.getPresentableText();
    }
    return result;
  }

  public void update(@NotNull final Collection<? extends ImplementationViewElement> elements, final int index) {
    update(elements, (psiElements, fileDescriptors) -> {
      if (myEditor.isDisposed()) return false;
      if (psiElements.length == 0) return false;

      final Project project = psiElements[0].getProject();
      myElements = psiElements;

      myIndex = index < myElements.length ? index : 0;
      VirtualFile virtualFile = myElements[myIndex].getContainingFile();

      EditorHighlighter highlighter;
      if (virtualFile != null) {
        highlighter = HighlighterFactory.createHighlighter(project, virtualFile);
        myEditor.setHighlighter(highlighter);
      }

      if (myElements.length > 1) {
        myFileChooser.setVisible(true);
        mySingleEntryPanel.setVisible(false);

        myFileChooser.setModel(new DefaultComboBoxModel<>(fileDescriptors.toArray(new FileDescriptor[0])));
        updateRenderer(project);
      }
      else {
        myFileChooser.setVisible(false);

        if (virtualFile != null) {
          updateSingleEntryLabel(virtualFile);
        }
      }

      updateControls();

      revalidate();
      repaint();

      return true;
    });
  }

  private static void update(@NotNull Collection<? extends ImplementationViewElement> elements,
                             @NotNull PairFunction<? super ImplementationViewElement[], ? super List<FileDescriptor>, Boolean> fun) {
    List<ImplementationViewElement> candidates = new ArrayList<>(elements.size());
    List<FileDescriptor> files = new ArrayList<>(elements.size());
    final Set<String> names = new HashSet<>();
    for (ImplementationViewElement element : elements) {
      if (element.isNamed()) {
        names.add(element.getName());
      }
      if (names.size() > 1) {
        break;
      }
    }

    for (ImplementationViewElement element : elements) {
      VirtualFile file = element.getContainingFile();
      if (file == null) continue;
      if (names.size() > 1) {
        files.add(new FileDescriptor(file, element));
      }
      else {
        files.add(new FileDescriptor(file, element.getContainingMemberOrSelf()));
      }
      candidates.add(element);
    }

    fun.fun(candidates.toArray(new ImplementationViewElement[0]), files);
  }

  private static Icon getIconForFile(VirtualFile virtualFile, Project project) {
    return IconUtil.getIcon(virtualFile, 0, project);
  }

  public JComponent getPreferredFocusableComponent() {
    return myElements.length > 1 ? myFileChooser : myEditor.getContentComponent();
  }

  @ApiStatus.Internal
  public ComboBox<FileDescriptor> getFileChooserComboBox() {
    return myFileChooser;
  }

  @ApiStatus.Internal
  public JPanel getSingleEntryPanel() {
    return mySingleEntryPanel;
  }

  @ApiStatus.Internal
  public JPanel getViewingPanel() {
    return myViewingPanel;
  }

  private void updateControls() {
    updateCombo();
    updateEditorText();
    myToolbar.updateActionsImmediately();
  }

  private void updateCombo() {
    if (myFileChooser != null && myFileChooser.isVisible()) {
      myFileChooser.setSelectedIndex(myIndex);
    }
  }

  private void updateEditorText() {
    disposeNonTextEditor();

    final ImplementationViewElement foundElement = myElements[myIndex];
    final Project project = foundElement.getProject();
    final VirtualFile vFile = foundElement.getContainingFile();
    if (vFile == null) return;

    for (ImplementationViewDocumentFactory documentFactory : ImplementationViewDocumentFactory.EP_NAME.getExtensions()) {
      Document document = documentFactory.createDocument(foundElement);
      if (document != null) {
        replaceEditor(project, vFile, documentFactory, document);
        return;
      }
    }

    final FileEditorProvider[] providers = FileEditorProviderManager.getInstance().getProviders(project, vFile);
    for (FileEditorProvider provider : providers) {
      if (provider instanceof QuickDefinitionProvider) {
        updateTextElement(foundElement);
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

  private void replaceEditor(Project project, VirtualFile vFile, ImplementationViewDocumentFactory documentFactory, Document document) {
    myViewingPanel.remove(myEditor.getComponent());
    factory.releaseEditor(myEditor);
    myEditor = (EditorEx)factory.createEditor(document, project, EditorKind.PREVIEW);
    tuneEditor(vFile);
    documentFactory.tuneEditorBeforeShow(myEditor);
    myViewingPanel.add(myEditor.getComponent(), TEXT_PAGE_KEY);
    myBinarySwitch.show(myViewingPanel, TEXT_PAGE_KEY);
    documentFactory.tuneEditorAfterShow(myEditor);
  }

  private void disposeNonTextEditor() {
    if (myNonTextEditor != null) {
      myCurrentNonTextEditorProvider.disposeEditor(myNonTextEditor);
      myNonTextEditor = null;
      myCurrentNonTextEditorProvider = null;
    }
  }

  private void updateTextElement(final ImplementationViewElement elt) {
    final String newText = elt.getText();
    if (newText == null || Comparing.strEqual(newText, myEditor.getDocument().getText())) return;
    DocumentUtil.writeInRunUndoTransparentAction(() -> {
      Document fragmentDoc = myEditor.getDocument();
      fragmentDoc.setReadOnly(false);

      fragmentDoc.replaceString(0, fragmentDoc.getTextLength(), newText);
      fragmentDoc.setReadOnly(true);

      PsiElement element = elt.getElementForShowUsages();
      PsiFile file = element == null ? null : element.getContainingFile();
      myEditor.getSettings().setTabSize(file != null ? CodeStyle.getIndentOptions(file).TAB_SIZE
                                                     : CodeStyle.getSettings(elt.getProject()).getTabSize(null));

      myEditor.getCaretModel().moveToOffset(0);
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    });
  }

  @Nullable
  public static String getNewText(PsiElement elt) {
    Project project = elt.getProject();
    PsiFile psiFile = getContainingFile(elt);

    final Document doc = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (doc == null) return null;

    if (elt.getTextRange() == null) {
      return null;
    }

    final ImplementationTextSelectioner implementationTextSelectioner =
      LanguageImplementationTextSelectioner.INSTANCE.forLanguage(elt.getLanguage());
    int start = implementationTextSelectioner.getTextStartOffset(elt);
    int end = implementationTextSelectioner.getTextEndOffset(elt);
    CharSequence rawDefinition = doc.getCharsSequence().subSequence(start, end);
    while (end > start && StringUtil.isLineBreak(rawDefinition.charAt(end - start - 1))) { // removing trailing EOLs from definition
      end--;
    }

    final int lineStart = doc.getLineStartOffset(doc.getLineNumber(start));
    final int lineEnd = end < doc.getTextLength() ? doc.getLineEndOffset(doc.getLineNumber(end)) : doc.getTextLength();
    final String text = doc.getCharsSequence().subSequence(lineStart, lineEnd).toString();
    final ImplementationTextProcessor processor = LanguageImplementationTextProcessor.INSTANCE.forLanguage(elt.getLanguage());
    return processor != null ? processor.process(text, elt) : text;
  }

  private static PsiFile getContainingFile(final PsiElement elt) {
    PsiFile psiFile = elt.getContainingFile();
    if (psiFile == null) return null;
    return psiFile.getOriginalFile();
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    if (ScreenUtil.isStandardAddRemoveNotify(this) && !myEditorReleased) {
      myEditorReleased = true; // remove notify can be called several times for popup windows
      EditorFactory.getInstance().releaseEditor(myEditor);
      disposeNonTextEditor();
    }
  }

  private ActionToolbar createToolbar(Consumer<ImplementationViewComponent> openUsageView) {
    DefaultActionGroup group = new DefaultActionGroup();

    BackAction back = new BackAction();
    back.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)), this);
    group.add(back);

    group.add(new ToolbarLabelAction() {
      @Override
      public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation,
                                                       @NotNull String place) {
        JComponent component = super.createCustomComponent(presentation, place);
        component.setBorder(JBUI.Borders.empty(0, 2));
        return component;
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        Presentation presentation = e.getPresentation();
        if (myElements != null && myElements.length > 1) {
          presentation.setText(myIndex + 1 + "/" + myElements.length);
          presentation.setVisible(true);
        }
        else {
          presentation.setVisible(false);
        }
      }
    });

    ForwardAction forward = new ForwardAction();
    forward.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)), this);
    group.add(forward);
    
    group.add(createGearActionButton(openUsageView));

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(IMPLEMENTATION_VIEW_PLACE, group, true);
    toolbar.setReservePlaceAutoPopupIcon(false);
    return toolbar;
  }

  private void goBack() {
    myIndex--;
    updateControls();
  }

  private void goForward() {
    myIndex++;
    updateControls();
  }

  public int getIndex() {
    return myIndex;
  }

  public ImplementationViewElement[] getElements() {
    return myElements;
  }

  public UsageView showInUsageView() {
    UIEventLogger.ImplementationViewToolWindowOpened.log(project);
    return FindUtil.showInUsageView(null, collectElementsForShowUsages(), myTitle, project);
  }

  private class BackAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    BackAction() {
      super(CodeInsightBundle.messagePointer("quick.definition.back"), AllIcons.Actions.Play_back);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      goBack();
    }


    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myIndex > 0);
      presentation.setVisible(myElements != null && myElements.length > 1);
    }
  }

  private class ForwardAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    ForwardAction() {
      super(CodeInsightBundle.messagePointer("quick.definition.forward"), AllIcons.Actions.Play_forward);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      goForward();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myElements != null && myIndex < myElements.length - 1);
      presentation.setVisible(myElements != null && myElements.length > 1);
    }
  }

  private class EditSourceAction extends EditSourceActionBase {
    EditSourceAction() {
      super(true, AllIcons.Actions.EditSource, CodeInsightBundle.message("quick.definition.edit.source"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      super.actionPerformed(e);
      if (myHint.isVisible()) {
        myHint.cancel();
      }
    }
  }

  private class EditSourceActionBase extends AnAction {
    private final boolean myFocusEditor;

    EditSourceActionBase(boolean focusEditor, Icon icon, @NlsActions.ActionText String text) {
      super(text, null, icon);
      myFocusEditor = focusEditor;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myFileChooser == null || !myFileChooser.isPopupVisible());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myElements[myIndex].navigate(myFocusEditor);
    }
  }

  private PsiElement[] collectElementsForShowUsages() {
    List<PsiElement> result = new ArrayList<>();
    for (ImplementationViewElement element : myElements) {
      PsiElement psiElement = element.getElementForShowUsages();
      if (psiElement != null) {
        result.add(psiElement);
      }
    }
    return PsiUtilCore.toPsiElementArray(result);
  }
}

