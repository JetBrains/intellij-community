/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.internal.psiView;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SortedComboBoxModel;
import com.intellij.ui.TitledBorderWithMnemonic;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class PsiViewerDialog extends DialogWrapper implements DataProvider {
  private final Project myProject;

  private final Tree myTree;
  private final ViewerTreeBuilder myTreeBuilder;

  private final JList myRefs;
  private static final String REFS_CACHE = "References Resolve Cache";

  private EditorEx myEditor;
  private String myLastParsedText = null;

  private JCheckBox myShowWhiteSpacesBox;
  private JPanel myStructureTreePanel;
  private JPanel myTextPanel;
  private JPanel myPanel;
  private JCheckBox myShowTreeNodesCheckBox;
  private JComboBox myDialectsComboBox;
  private JPanel myReferencesPanel;
  private JPanel myButtonPanel;
  private JSplitPane myTextSplit;
  private JSplitPane myTreeSplit;
  private final Presentation myPresentation = new Presentation();
  private final Map<String, Object> handlers = new HashMap<String, Object>();
  private DefaultActionGroup myGroup;
  private Language[] myLanguageDialects;
  private final Color SELECTION_BG_COLOR = Registry.getColor("psi.viewer.selection.color", new Color(255, 204, 204));

  private static final Comparator<Language> DIALECTS_COMPARATOR = new Comparator<Language>() {
    public int compare(final Language o1, final Language o2) {
      if (o1 == null) return o2 == null ? 0 : -1;
      if (o2 == null) return 1;
      return o1.getID().compareTo(o2.getID());
    }
  };
  private final EditorListener myEditorListener = new EditorListener();
  private int myLastParsedTextHashCode = 17;
  private int myNewDocumentHashCode = 11;

  @Nullable
  private static PsiElement findCommonParent(PsiElement start, PsiElement end) {
    final TextRange range = end.getTextRange();
    while (start != null && !start.getTextRange().contains(range)) {
      start = start.getParent();
    }
    return start;
  }

  public PsiViewerDialog(Project project, boolean modal) {
    super(project, true);
    setTitle("PSI Viewer");
    myProject = project;
    myTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    UIUtil.setLineStyleAngled(myTree);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.updateUI();
    ToolTipManager.sharedInstance().registerComponent(myTree);
    TreeUtil.installActions(myTree);
    new TreeSpeedSearch(myTree);
    myTreeBuilder = new ViewerTreeBuilder(project, myTree);

    myTree.addTreeSelectionListener(new MyTreeSelectionListener());

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(scrollPane, BorderLayout.CENTER);
    myStructureTreePanel.setLayout(new BorderLayout());
    myStructureTreePanel.add(panel, BorderLayout.CENTER);

    myRefs = new JBList(new DefaultListModel());
    JScrollPane refScrollPane = ScrollPaneFactory.createScrollPane(myRefs);
    JPanel refPanel = new JPanel(new BorderLayout());
    refPanel.add(refScrollPane, BorderLayout.CENTER);
    myReferencesPanel.setLayout(new BorderLayout());
    myReferencesPanel.add(refPanel, BorderLayout.CENTER);
    final GoToListener listener = new GoToListener();
    myRefs.addKeyListener(listener);
    myRefs.addMouseListener(listener);
    myRefs.getSelectionModel().addListSelectionListener(listener);
    myRefs.setCellRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (resolve(index) == null) {
          comp.setForeground(Color.red);
        }
        return comp;
      }
    });

    setModal(modal);
    setOKButtonText("&Build PSI Tree");
    init();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.internal.psiView.PsiViewerDialog";
  }

  @Override
  protected String getHelpId() {
    return "reference.psi.viewer";
  }

  public JComponent getPreferredFocusedComponent() {
    return myEditor.getContentComponent();
  }

  private void updatePresentation(Presentation p) {
    myPresentation.setText(p.getText());
    myPresentation.setIcon(p.getIcon());
  }

  protected void init() {
    initBorders();
    final List<Presentation> items = new ArrayList<Presentation>();
    final EditorFactory editorFactory = EditorFactory.getInstance();
    final Document document = editorFactory.createDocument("");
    myEditor = (EditorEx)editorFactory.createEditor(document, myProject);
    myEditor.getSettings().setFoldingOutlineShown(false);
    document.addDocumentListener(myEditorListener);
    myEditor.getSelectionModel().addSelectionListener(myEditorListener);
    myEditor.getCaretModel().addCaretListener(myEditorListener);

    for (PsiViewerExtension extension : Extensions.getExtensions(PsiViewerExtension.EP_NAME)) {
      final Presentation p = new Presentation(extension.getName());
      p.setIcon(extension.getIcon());
      handlers.put(p.getText(), extension);
      items.add(p);
    }

    Set<FileType> allFileTypes = new HashSet<FileType>();
    Collections.addAll(allFileTypes, FileTypeManager.getInstance().getRegisteredFileTypes());
    for (Language language : Language.getRegisteredLanguages()) {
      FileType fileType = language.getAssociatedFileType();
      if (fileType != null) {
        allFileTypes.add(fileType);
      }
    }
    for (FileType fileType : allFileTypes) {
      if (fileType != StdFileTypes.GUI_DESIGNER_FORM &&
          fileType != StdFileTypes.IDEA_MODULE &&
          fileType != StdFileTypes.IDEA_PROJECT &&
          fileType != StdFileTypes.IDEA_WORKSPACE &&
          fileType != FileTypes.ARCHIVE &&
          fileType != FileTypes.UNKNOWN &&
          fileType != FileTypes.PLAIN_TEXT &&
          !(fileType instanceof AbstractFileType) &&
          !fileType.isBinary() &&
          !fileType.isReadOnly()) {
        final Presentation p = new Presentation(fileType.getName() + " file");
        p.setIcon(fileType.getIcon());
        handlers.put(p.getText(), fileType);
        items.add(p);
      }
    }

    final Presentation[] popupItems = items.toArray(new Presentation[items.size()]);
    Arrays.sort(popupItems, new Comparator<Presentation>() {
      public int compare(Presentation p1, Presentation p2) {
        return p1.getText().toUpperCase().compareTo(p2.getText().toUpperCase());
      }
    });

    final ViewerTreeStructure treeStructure = (ViewerTreeStructure)myTreeBuilder.getTreeStructure();
    myShowWhiteSpacesBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        treeStructure.setShowWhiteSpaces(myShowWhiteSpacesBox.isSelected());
        myTreeBuilder.queueUpdate();
      }
    });
    myShowTreeNodesCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        treeStructure.setShowTreeNodes(myShowTreeNodesCheckBox.isSelected());
        myTreeBuilder.queueUpdate();
      }
    });
    myTextPanel.setLayout(new BorderLayout());
    myTextPanel.add(myEditor.getComponent(), BorderLayout.CENTER);

    myGroup = new DefaultActionGroup();
    for (final Presentation popupItem : popupItems) {
      myGroup.add(new PopupItemAction(popupItem));
    }

    final PsiViewerSettings settings = PsiViewerSettings.getSettings();
    final String type = settings.type;
    for (Presentation popupItem : popupItems) {
      if (popupItem.getText().equals(type)) {
        updatePresentation(popupItem);
        break;
      }
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myEditor.getDocument().setText(settings.text);
        myEditor.getSelectionModel().setSelection(0, settings.text.length());
      }
    });

    myShowWhiteSpacesBox.setSelected(settings.showWhiteSpaces);
    treeStructure.setShowWhiteSpaces(settings.showWhiteSpaces);
    myShowTreeNodesCheckBox.setSelected(settings.showTreeNodes);
    treeStructure.setShowTreeNodes(settings.showTreeNodes);

    final ChoosePsiTypeButton typeButton = new ChoosePsiTypeButton();
    myButtonPanel.add(typeButton.createCustomComponent(myPresentation), BorderLayout.CENTER);

    updateDialectsCombo();
    myDialectsComboBox.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index,
                                                    boolean isSelected, boolean cellHasFocus) {
        final Component result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value == null) setText("<no dialect>");
        return result;
      }
    });

    if (myDialectsComboBox.isVisible()) {
      for (int i = 0; i < myLanguageDialects.length; i++) {
        if (settings.dialect.equals(myLanguageDialects[i].toString())) {
          myDialectsComboBox.setSelectedIndex(i+1);
          break;
        }
      }
    }

    registerCustomKeyboardActions();
    final Dimension size = DimensionService.getInstance().getSize(getDimensionServiceKey(), myProject);
    if (size == null) {
      DimensionService.getInstance().setSize(getDimensionServiceKey(), new Dimension(600, 600));
    }
    myTextSplit.setDividerLocation(settings.textDividerLocation);
    myTreeSplit.setDividerLocation(settings.treeDividerLocation);

    updateEditor();
    super.init();
  }

  private void registerCustomKeyboardActions() {
    final Component component = myButtonPanel.getComponents()[0];
    if (component instanceof JComponent) {
      final Component button = ((JComponent)component).getComponents()[0];
      if (button instanceof JButton) {
        final JButton jButton = (JButton)button;
        final int mask = SystemInfo.isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.ALT_DOWN_MASK;
        registerKeyboardAction(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            jButton.doClick();
          }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_P, mask));

        registerKeyboardAction(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            focusEditor();
          }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_T, mask));

        registerKeyboardAction(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            focusTree();
          }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_S, mask));

        registerKeyboardAction(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            focusTree();
          }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_S, mask));


        registerKeyboardAction(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            focusTree();
          }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_S, mask));

        registerKeyboardAction(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            focusRefs();
          }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_R, mask));

        registerKeyboardAction(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            if (myRefs.isFocusOwner()) {
              focusTree();
            }
            else if (myTree.isFocusOwner()) {
              focusRefs();
            }
          }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
      }
    }
  }

  private void registerKeyboardAction(ActionListener actionListener, KeyStroke keyStroke) {
    getRootPane().registerKeyboardAction(actionListener, keyStroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  private void focusEditor() {
    IdeFocusManager.getInstance(myProject).requestFocus(myEditor.getContentComponent(), true);
  }

  private void focusTree() {
    IdeFocusManager.getInstance(myProject).requestFocus(myTree, true);
  }

  private void focusRefs() {
    IdeFocusManager.getInstance(myProject).requestFocus(myRefs, true);
    if (myRefs.getModel().getSize() > 0) {
      if (myRefs.getSelectedIndex() == -1) {
        myRefs.setSelectedIndex(0);
      }
    }
  }

  private void initBorders() {
    myTextPanel.setBorder(new TitledBorderWithMnemonic("&Text"));
    myStructureTreePanel.setBorder(new TitledBorderWithMnemonic("PSI &Structure"));
    myReferencesPanel.setBorder(new TitledBorderWithMnemonic("&References"));
  }

  @Nullable
  private PsiElement getPsiElement() {
    final TreePath path = myTree.getSelectionPath();
    return path == null ? null : getPsiElement((DefaultMutableTreeNode)path.getLastPathComponent());
  }

  @Nullable
  private static PsiElement getPsiElement(DefaultMutableTreeNode node) {
    if (node.getUserObject() instanceof ViewerNodeDescriptor) {
      ViewerNodeDescriptor descriptor = (ViewerNodeDescriptor)node.getUserObject();
      Object elementObject = descriptor.getElement();
      return elementObject instanceof PsiElement
             ? (PsiElement)elementObject
             : elementObject instanceof ASTNode ? ((ASTNode)elementObject).getPsi() : null;
    }
    return null;
  }

  private void updateDialectsCombo() {
    final SortedComboBoxModel<Language> model = new SortedComboBoxModel<Language>(DIALECTS_COMPARATOR);
    final Object handler = getHandler();
    if (handler instanceof LanguageFileType) {
      final Language baseLang = ((LanguageFileType)handler).getLanguage();
      myLanguageDialects = LanguageUtil.getLanguageDialects(baseLang);
      Arrays.sort(myLanguageDialects, DIALECTS_COMPARATOR);
      model.setAll(myLanguageDialects);
      model.add(null);
    }
    myDialectsComboBox.setModel(model);
    myDialectsComboBox.setVisible(model.getSize() > 1);
    if (!myDialectsComboBox.isVisible()) {
      myLanguageDialects = new Language[0];
    }
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private Object getHandler() {
    return handlers.get(myPresentation.getText());
  }

  protected void doOKAction() {
    final String text = myEditor.getDocument().getText();
    //if (text.trim().length() == 0) return;

    myLastParsedText = text;
    myLastParsedTextHashCode = text.hashCode();
    myNewDocumentHashCode = myLastParsedTextHashCode;
    PsiElement rootElement = null;
    final Object handler = getHandler();

    try {
      if (handler instanceof PsiViewerExtension) {
        final PsiViewerExtension ext = (PsiViewerExtension)handler;
        rootElement = ext.createElement(myProject, text);
      }
      else if (handler instanceof FileType) {
        final FileType type = (FileType)handler;
        if (type instanceof LanguageFileType) {
          final Language language = ((LanguageFileType)type).getLanguage();
          final Language dialect = (Language)myDialectsComboBox.getSelectedItem();
          rootElement = PsiFileFactory.getInstance(myProject)
            .createFileFromText("Dummy." + type.getDefaultExtension(), dialect == null ? language : dialect, text);
        }
        else {
          rootElement = PsiFileFactory.getInstance(myProject).createFileFromText("Dummy." + type.getDefaultExtension(), text);
        }
      }
      focusTree();
    }
    catch (IncorrectOperationException e1) {
      rootElement = null;
      Messages.showMessageDialog(myProject, e1.getMessage(), "Error", Messages.getErrorIcon());
    }
    ViewerTreeStructure structure = (ViewerTreeStructure)myTreeBuilder.getTreeStructure();
    structure.setRootPsiElement(rootElement);

    myTreeBuilder.queueUpdate();
    myTree.setRootVisible(true);
    myTree.expandRow(0);
    myTree.setRootVisible(false);
  }

  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.NAVIGATABLE.is(dataId)) {
      String fqn = null;
      if (myTree.hasFocus()) {
        final TreePath path = myTree.getSelectionPath();
        if (path != null) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
          if (!(node.getUserObject() instanceof ViewerNodeDescriptor)) return null;
          ViewerNodeDescriptor descriptor = (ViewerNodeDescriptor)node.getUserObject();
          Object elementObject = descriptor.getElement();
          final PsiElement element = elementObject instanceof PsiElement
                                     ? (PsiElement)elementObject
                                     : elementObject instanceof ASTNode ? ((ASTNode)elementObject).getPsi() : null;
          if (element != null) {
            fqn = element.getClass().getName();
          }
        }
      } else if (myRefs.hasFocus()) {
        final Object value = myRefs.getSelectedValue();
        if (value instanceof String) {
          fqn = (String)value;
        }
      }
      if (fqn != null) {
        return getContainingFileForClass(fqn);
      }
    }
    return null;
  }

  private class MyTreeSelectionListener implements TreeSelectionListener {
    private final TextAttributes myAttributes;

    public MyTreeSelectionListener() {
      myAttributes = new TextAttributes();
      myAttributes.setBackgroundColor(SELECTION_BG_COLOR);
      myAttributes.setForegroundColor(Color.white);
    }

    public void valueChanged(TreeSelectionEvent e) {
      if (!myEditor.getDocument().getText().equals(myLastParsedText)) return;
      TreePath path = myTree.getSelectionPath();
      if (path == null) {
        clearSelection();
      }
      else {
        clearSelection();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        if (!(node.getUserObject() instanceof ViewerNodeDescriptor)) return;
        ViewerNodeDescriptor descriptor = (ViewerNodeDescriptor)node.getUserObject();
        Object elementObject = descriptor.getElement();
        final PsiElement element = elementObject instanceof PsiElement
                                   ? (PsiElement)elementObject
                                   : elementObject instanceof ASTNode ? ((ASTNode)elementObject).getPsi() : null;
        if (element != null) {
          TextRange range = element.getTextRange();
          int start = range.getStartOffset();
          int end = range.getEndOffset();
          final ViewerTreeStructure treeStructure = (ViewerTreeStructure)myTreeBuilder.getTreeStructure();
          PsiElement rootPsiElement = treeStructure.getRootPsiElement();
          if (rootPsiElement != null) {
            int baseOffset = rootPsiElement.getTextRange().getStartOffset();
            start -= baseOffset;
            end -= baseOffset;
          }

          final int textLength = myEditor.getDocument().getTextLength();
          if (end <= textLength) {
            myEditor.getMarkupModel()
              .addRangeHighlighter(start, end, HighlighterLayer.LAST, myAttributes, HighlighterTargetArea.EXACT_RANGE);
            if (myTree.hasFocus()) {
              myEditor.getCaretModel().moveToOffset(start);
              myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
            } else {
              myEditor.getScrollingModel().scrollTo(myEditor.offsetToLogicalPosition(start), ScrollType.MAKE_VISIBLE);
            }
          }
          updateReferences(element);
        }
      }
    }

    public void updateReferences(PsiElement element) {
      final DefaultListModel model = (DefaultListModel)myRefs.getModel();
      model.clear();
      final Object cache = myRefs.getClientProperty(REFS_CACHE);
      if (cache instanceof Map) {
        ((Map)cache).clear();
      } else {
        myRefs.putClientProperty(REFS_CACHE, new HashMap());
      }
      if (element != null) {
        for (PsiReference reference : element.getReferences()) {
          model.addElement(reference.getClass().getName());
        }
      }
    }

    private void clearSelection() {
      myEditor.getMarkupModel().removeAllHighlighters();
    }
  }

  public void doCancelAction() {
    final PsiViewerSettings settings = PsiViewerSettings.getSettings();
    settings.type = myPresentation.getText();
    settings.text = myEditor.getDocument().getText();
    settings.showTreeNodes = myShowTreeNodesCheckBox.isSelected();
    settings.showWhiteSpaces = myShowWhiteSpacesBox.isSelected();
    final Object selectedDialect = myDialectsComboBox.getSelectedItem();
    settings.dialect = myDialectsComboBox.isVisible() && selectedDialect != null ? selectedDialect.toString() : "";
    settings.textDividerLocation = myTextSplit.getDividerLocation();
    settings.treeDividerLocation = myTreeSplit.getDividerLocation();    
    super.doCancelAction();
  }

  public void dispose() {
    Disposer.dispose(myTreeBuilder);
    EditorFactory.getInstance().releaseEditor(myEditor);

    super.dispose();
  }

  @Nullable
  private PsiElement resolve(int index) {
    final PsiElement element = getPsiElement();
    if (element == null) return null;
    Object o = myRefs.getClientProperty(REFS_CACHE);
    if (o == null) {
      myRefs.putClientProperty(REFS_CACHE, o = new HashMap());
    }
    HashMap map = (HashMap)o;
    Object cache = map.get(element);
    if (cache == null) {
      final PsiReference[] references = element.getReferences();
      cache = new PsiElement[references.length];
      for (int i = 0; i < references.length; i++) {
        ((PsiElement[])cache)[i] = references[i].resolve();
      }
      map.put(element, cache);
    }
    PsiElement[] elements = (PsiElement[])cache;
    return index >= elements.length ? null : elements[index];
  }

  @Nullable
  private PsiFile getContainingFileForClass(String fqn) {
    String filename = fqn;
    if (fqn.contains(".")) {
      filename = fqn.substring(fqn.lastIndexOf('.') + 1);
    }
    if (filename.contains("$")) {
      filename = filename.substring(0, filename.indexOf('$'));
    }
    filename += ".java";
    final PsiFile[] files = FilenameIndex.getFilesByName(myProject, filename, GlobalSearchScope.allScope(myProject));
    if (files != null && files.length > 0) {
      return files[0];
    }
    return null;
  }


  @Nullable
  public static TreeNode findNodeWithObject(final Object object, final TreeModel model, final Object parent) {
    for (int i = 0; i < model.getChildCount(parent); i++) {
      final DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) model.getChild(parent, i);
      if (childNode.getUserObject().equals(object)) {
        return childNode;
      } else {
        final TreeNode node = findNodeWithObject(object, model, childNode);
        if (node != null) return node;
      }
    }
    return null;
  }

  private class ChoosePsiTypeButton extends ComboBoxAction {
    protected int getMaxRows() {
      return 15;
    }

    protected int getMinWidth() {
      return 150;
    }

    protected int getMinHeight() {
      return 200;
    }

    @NotNull
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      return myGroup;
    }
  }

  private class GoToListener implements KeyListener, MouseListener, ListSelectionListener {
    private RangeHighlighter myHighlighter;
    private final TextAttributes myAttributes =
      new TextAttributes(Color.white, SELECTION_BG_COLOR, Color.red, EffectType.BOXED, Font.PLAIN);

    private void navigate() {
      clearSelection();
      final Object value = myRefs.getSelectedValue();
      if (value instanceof String) {
        final String fqn = (String)value;
        final PsiFile file = getContainingFileForClass(fqn);
        if (file != null) file.navigate(true);
      }
    }

    public void keyPressed(KeyEvent e) {
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        navigate();
      }
    }

    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() > 1) {
        navigate();
      }
    }

    public void valueChanged(ListSelectionEvent e) {
      clearSelection();
      updateDialectsCombo();
      final int ind = myRefs.getSelectedIndex();
      final PsiElement element = getPsiElement();
      if (ind > -1 && element != null) {
        final PsiReference[] references = element.getReferences();
        if (ind < references.length) {
          final TextRange textRange = references[ind].getRangeInElement();
          TextRange range = element.getTextRange();
          int start = range.getStartOffset();
          int end = range.getEndOffset();
          final ViewerTreeStructure treeStructure = (ViewerTreeStructure)myTreeBuilder.getTreeStructure();
          PsiElement rootPsiElement = treeStructure.getRootPsiElement();
          if (rootPsiElement != null) {
            int baseOffset = rootPsiElement.getTextRange().getStartOffset();
            start -= baseOffset;
            end -= baseOffset;
          }

          start += textRange.getStartOffset();
          end = start + textRange.getLength();
          myHighlighter = myEditor.getMarkupModel()
            .addRangeHighlighter(start, end, HighlighterLayer.FIRST + 1, myAttributes, HighlighterTargetArea.EXACT_RANGE);
        }
      }
    }

    public void clearSelection() {
      if (myHighlighter != null && Arrays.asList(myEditor.getMarkupModel().getAllHighlighters()).contains(myHighlighter)) {
        myEditor.getMarkupModel().removeHighlighter(myHighlighter);
        myHighlighter = null;
      }
    }

    public void keyTyped(KeyEvent e) {}
    public void keyReleased(KeyEvent e) {}
    public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
  }

  private class PopupItemAction extends AnAction implements DumbAware {
    public PopupItemAction(Presentation p) {
      super(p.getText(), p.getText(), p.getIcon());
    }

    public void actionPerformed(AnActionEvent e) {
      updatePresentation(e.getPresentation());
      updateDialectsCombo();
      updateEditor();
    }
  }

  private FileType getFileType() {
    Object handler = getHandler();
    return handler instanceof FileType ? (FileType)handler :
      handler instanceof PsiViewerExtension ? ((PsiViewerExtension)handler).getDefaultFileType() : PlainTextFileType.INSTANCE;
  }

  private void updateEditor() {
    myEditor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, getFileType()));
  }

  private class EditorListener implements CaretListener, SelectionListener, DocumentListener {
    public void caretPositionChanged(CaretEvent e) {
      if (!available() || myEditor.getSelectionModel().hasSelection()) return;
      final PsiFile psiFile = getPsiFile();
      if (psiFile == null) return;
      final int offset = myEditor.getCaretModel().getOffset();
      final PsiElement element = PsiUtilBase.getElementAtOffset(psiFile, offset);
      myTreeBuilder.select(element);
    }

    public void selectionChanged(SelectionEvent e) {
      if (!available() || !myEditor.getSelectionModel().hasSelection()) return;
      final PsiFile psiFile = getPsiFile();
      if (psiFile == null) return;
      final SelectionModel selection = myEditor.getSelectionModel();
      final int start = selection.getSelectionStart();
      final int end = selection.getSelectionEnd();
      final PsiElement element = findCommonParent(PsiUtilBase.getElementAtOffset(psiFile, start), PsiUtilBase.getElementAtOffset(psiFile, end));
      myTreeBuilder.select(element);
    }

    private boolean available() {
      return myLastParsedTextHashCode == myNewDocumentHashCode && myEditor.getContentComponent().hasFocus();
    }

    @Nullable
    private PsiFile getPsiFile() {
      final PsiElement root = ((ViewerTreeStructure)myTreeBuilder.getTreeStructure()).getRootPsiElement();
      return root instanceof PsiFile ? (PsiFile)root : null;
    }

    public void beforeDocumentChange(DocumentEvent event) {

    }
    public void documentChanged(DocumentEvent event) {
      myNewDocumentHashCode = event.getDocument().getText().hashCode();
    }
  }
}
