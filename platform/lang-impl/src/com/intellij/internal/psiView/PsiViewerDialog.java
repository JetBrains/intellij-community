/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.diagnostic.LogMessageEx;
import com.intellij.diagnostic.errordialog.Attachment;
import com.intellij.formatting.ASTBlock;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.internal.psiView.formattingblocks.BlockTreeBuilder;
import com.intellij.internal.psiView.formattingblocks.BlockTreeNode;
import com.intellij.internal.psiView.formattingblocks.BlockTreeStructure;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiReference;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
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
import java.util.regex.Pattern;

/**
 * @author Konstantin Bulenkov
 */
public class PsiViewerDialog extends DialogWrapper implements DataProvider, Disposable {
  private static final String REFS_CACHE = "References Resolve Cache";
  private static final Color SELECTION_BG_COLOR = Registry.getColor("psi.viewer.selection.color", new Color(255, 204, 204));
  private static final Logger LOG = Logger.getInstance("#com.intellij.internal.psiView.PsiViewerDialog");
  private final Project myProject;

  private JPanel myPanel;
  private JComboBox myFileTypeComboBox;
  private JCheckBox myShowWhiteSpacesBox;
  private JCheckBox myShowTreeNodesCheckBox;
  private JBLabel myDialectLabel;
  private JComboBox myDialectComboBox;
  private JLabel myExtensionLabel;
  private JComboBox myExtensionComboBox;
  private JPanel myTextPanel;
  private JPanel myStructureTreePanel;
  private JPanel myReferencesPanel;
  private JSplitPane myTextSplit;
  private JSplitPane myTreeSplit;
  private Tree myPsiTree;
  private ViewerTreeBuilder myPsiTreeBuilder;
  private JList myRefs;

  private Tree myBlockTree;
  private JPanel myBlockStructurePanel;
  private JSplitPane myBlockRefSplitPane;
  private JCheckBox myShowBlocksCheckBox;
   private TitledSeparatorWithMnemonic myTextSeparator;
  private TitledSeparatorWithMnemonic myPsiTreeSeparator;
  private TitledSeparatorWithMnemonic myRefsSeparator;
  private TitledSeparatorWithMnemonic myBlockTreeSeparator;
  @Nullable
  private BlockTreeBuilder myBlockTreeBuilder;
  private RangeHighlighter myHighlighter;
  private RangeHighlighter myIntersectHighlighter;
  private HashMap<PsiElement, BlockTreeNode> myPsiToBlockMap;

  private final Set<SourceWrapper> mySourceWrappers = Sets.newTreeSet();
  private final EditorEx myEditor;
  private final EditorListener myEditorListener = new EditorListener();
  private String myLastParsedText = null;
  private int myLastParsedTextHashCode = 17;
  private int myNewDocumentHashCode = 11;

  private int myIgnoreBlockTreeSelectionMarker = 0;

  private PsiFile myCurrentFile;
  private String myInitText;
  private String myFileType;

  private void createUIComponents() {
    myPsiTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    myBlockTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    myRefs = new JBList(new DefaultListModel());
  }

  private static class ExtensionComparator implements Comparator<String> {
    private final String myOnTop;

    public ExtensionComparator(String onTop) {
      myOnTop = onTop;
    }

    @Override
    public int compare(String o1, String o2) {
      if (o1.equals(myOnTop)) return -1;
      if (o2.equals(myOnTop)) return 1;
      return o1.compareToIgnoreCase(o2);
    }
  }

  private static class DialectsComparator implements Comparator<Language> {
    private final Language myDefault;

    public DialectsComparator(final Language aDefault) {
      myDefault = aDefault;
    }

    @Override
    public int compare(final Language o1, final Language o2) {
      if (myDefault.equals(o1)) return -1;
      if (myDefault.equals(o2)) return 1;
      return o1.getID().compareToIgnoreCase(o2.getID());
    }
  }

  private static class SourceWrapper implements Comparable<SourceWrapper> {
    private final FileType myFileType;
    private final PsiViewerExtension myExtension;

    public SourceWrapper(final FileType fileType) {
      myFileType = fileType;
      myExtension = null;
    }

    public SourceWrapper(final PsiViewerExtension extension) {
      myFileType = null;
      myExtension = extension;
    }

    public String getText() {
      return myFileType != null ? myFileType.getName() + " file" : myExtension.getName();
    }

    @Nullable
    public Icon getIcon() {
      return myFileType != null ? myFileType.getIcon() : myExtension.getIcon();
    }

    @Override
    public int compareTo(final SourceWrapper o) {
      return o == null ? -1 : getText().compareToIgnoreCase(o.getText());
    }
  }

  public PsiViewerDialog(Project project, boolean modal, @Nullable PsiFile currentFile, @Nullable Editor currentEditor) {
    super(project, true);
    myCurrentFile = currentFile;
    myProject = project;
    setModal(modal);
    setOKButtonText("&Build PSI Tree");
    setCancelButtonText("&Close");
    Disposer.register(myProject, getDisposable());
    EditorEx editor = null;
    if (myCurrentFile == null) {
      setTitle("PSI Viewer");
    }
    else {
      setTitle("PSI Context Viewer: " + myCurrentFile.getName());
      myFileType = myCurrentFile.getLanguage().getDisplayName();
      if (currentEditor != null) {
        myInitText = currentEditor.getSelectionModel().getSelectedText();
      }
      if (myInitText == null) {
        myInitText = currentFile.getText();
        editor = (EditorEx)EditorFactory.getInstance().createEditor(currentFile.getViewProvider().getDocument(), myProject);
      }
    }
    if (editor == null) {
      final Document document = EditorFactory.getInstance().createDocument("");
      editor = (EditorEx)EditorFactory.getInstance().createEditor(document, myProject);
    }
    editor.getSettings().setLineMarkerAreaShown(false);
    myEditor = editor;
    init();
    if (myCurrentFile != null) {
      doOKAction();
    }
  }

  @Override
  protected void init() {
    initMnemonics();

    initTree(myPsiTree);
    final TreeCellRenderer renderer = myPsiTree.getCellRenderer();
    myPsiTree.setCellRenderer(new TreeCellRenderer() {
      @Override
      public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        final Component c = renderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        if (value instanceof DefaultMutableTreeNode) {
          final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
          if (userObject instanceof ViewerNodeDescriptor) {
            final Object element = ((ViewerNodeDescriptor)userObject).getElement();
            if (c instanceof NodeRenderer) {
              ((NodeRenderer)c).setToolTipText(element == null ? null : element.getClass().getName());
            }
            if ((element instanceof PsiElement && FileContextUtil.getFileContext(((PsiElement)element).getContainingFile()) != null) ||
                element instanceof ViewerTreeStructure.Inject) {
              final TextAttributes attr = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.INJECTED_LANGUAGE_FRAGMENT);
              c.setBackground(attr.getBackgroundColor());
            }
          }
        }
        return c;
      }
    });
    myPsiTreeBuilder = new ViewerTreeBuilder(myProject, myPsiTree);
    Disposer.register(getDisposable(), myPsiTreeBuilder);
    myPsiTree.addTreeSelectionListener(new MyPsiTreeSelectionListener());

    final GoToListener listener = new GoToListener();
    myRefs.addKeyListener(listener);
    myRefs.addMouseListener(listener);
    myRefs.getSelectionModel().addListSelectionListener(listener);
    myRefs.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (resolve(index) == null) {
          comp.setForeground(Color.red);
        }
        return comp;
      }
    });

    initTree(myBlockTree);

    myEditor.getSettings().setFoldingOutlineShown(false);
    myEditor.getDocument().addDocumentListener(myEditorListener);
    myEditor.getSelectionModel().addSelectionListener(myEditorListener);
    myEditor.getCaretModel().addCaretListener(myEditorListener);

    getPeer().getWindow().setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() {
      @Override
      public Component getInitialComponent(Window window) {
        return myEditor.getComponent();
      }
    });
    final PsiViewerSettings settings = PsiViewerSettings.getSettings();
    final String type = myFileType != null ? myFileType : settings.type;
    SourceWrapper lastUsed = null;
    for (PsiViewerExtension extension : Extensions.getExtensions(PsiViewerExtension.EP_NAME)) {
      final SourceWrapper wrapper = new SourceWrapper(extension);
      mySourceWrappers.add(wrapper);
    }
    final Set<FileType> allFileTypes = Sets.newHashSet();
    Collections.addAll(allFileTypes, FileTypeManager.getInstance().getRegisteredFileTypes());
    for (Language language : Language.getRegisteredLanguages()) {
      final FileType fileType = language.getAssociatedFileType();
      if (fileType != null) {
        allFileTypes.add(fileType);
      }
    }
    Language curLanguage = myCurrentFile != null ? myCurrentFile.getLanguage() : null;
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
        final SourceWrapper wrapper = new SourceWrapper(fileType);
        mySourceWrappers.add(wrapper);
        if (lastUsed == null && wrapper.getText().equals(type)) lastUsed = wrapper;
        if (myCurrentFile != null && wrapper.myFileType instanceof LanguageFileType &&
            ((LanguageFileType)wrapper.myFileType).getLanguage().is(curLanguage)) {
          lastUsed = wrapper;
        }
      }
    }
    myFileTypeComboBox.setModel(new CollectionComboBoxModel(Lists.newArrayList(mySourceWrappers), lastUsed));
    myFileTypeComboBox.setRenderer(new ListCellRendererWrapper<SourceWrapper>(myFileTypeComboBox.getRenderer()) {
      @Override
      public void customize(JList list, SourceWrapper value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
          setText(value.getText());
          setIcon(value.getIcon());
        }
      }
    });
    myFileTypeComboBox.setKeySelectionManager(new JComboBox.KeySelectionManager() {
      private static final int TIMEOUT = 1000;  // ms
      private final StringBuilder myPrefix = new StringBuilder();
      private long myTimeStamp = 0;

      @Override
      public int selectionForKey(char ch, ComboBoxModel model) {
        final long now = System.currentTimeMillis();
        if (now - myTimeStamp > TIMEOUT) {
          myPrefix.delete(0, myPrefix.length());
        }
        myTimeStamp = now;

        if (Character.isLetterOrDigit(ch)) {
          myPrefix.append(ch);
        }
        else if (ch == '\b') {
          myPrefix.delete(0, myPrefix.length());
          return 0;
        }

        final String prefix = myPrefix.toString().toLowerCase();
        for (int i = 0, size = model.getSize(); i < size; i++) {
          final SourceWrapper item = (SourceWrapper)model.getElementAt(i);
          if (item.getText().toLowerCase().startsWith(prefix)) {
            return i;
          }
        }

        return -1;
      }
    });
    myFileTypeComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateDialectsCombo(null);
        updateExtensionsCombo();
        updateEditor();
      }
    });
    myDialectComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateEditor();
      }
    });
    myFileTypeComboBox.addFocusListener(new AutoExpandFocusListener(myFileTypeComboBox));
    if (myCurrentFile == null && lastUsed == null && mySourceWrappers.size() > 0) {
      myFileTypeComboBox.setSelectedIndex(0);
    }

    myDialectComboBox.setRenderer(new ListCellRendererWrapper<Language>(myDialectComboBox.getRenderer()) {
      @Override
      public void customize(final JList list, final Language value, final int index, final boolean selected, final boolean hasFocus) {
        setText(value != null ? value.getDisplayName() : "<default>");
      }
    });
    myDialectComboBox.addFocusListener(new AutoExpandFocusListener(myDialectComboBox));
    myExtensionComboBox.setRenderer(new ListCellRendererWrapper<String>(myExtensionComboBox.getRenderer()) {
      @Override
      public void customize(JList list, String value, int index, boolean selected, boolean hasFocus) {
        if (value != null) setText("." + value);
      }
    });
    myExtensionComboBox.addFocusListener(new AutoExpandFocusListener(myExtensionComboBox));

    final ViewerTreeStructure psiTreeStructure = (ViewerTreeStructure)myPsiTreeBuilder.getTreeStructure();
    myShowWhiteSpacesBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        psiTreeStructure.setShowWhiteSpaces(myShowWhiteSpacesBox.isSelected());
        myPsiTreeBuilder.queueUpdate();
      }
    });
    myShowTreeNodesCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        psiTreeStructure.setShowTreeNodes(myShowTreeNodesCheckBox.isSelected());
        myPsiTreeBuilder.queueUpdate();
      }
    });
    myShowWhiteSpacesBox.setSelected(settings.showWhiteSpaces);
    psiTreeStructure.setShowWhiteSpaces(settings.showWhiteSpaces);
    myShowTreeNodesCheckBox.setSelected(settings.showTreeNodes);
    psiTreeStructure.setShowTreeNodes(settings.showTreeNodes);
    myShowBlocksCheckBox.setSelected(settings.showBlocks);
    myBlockStructurePanel.setVisible(settings.showBlocks);
    myShowBlocksCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!myShowBlocksCheckBox.isSelected()) {
          settings.blockRefDividerLocation = myBlockRefSplitPane.getDividerLocation();
        }
        else {
          myBlockRefSplitPane.setDividerLocation(settings.blockRefDividerLocation);
        }
        myBlockStructurePanel.setVisible(myShowBlocksCheckBox.isSelected());
        myBlockStructurePanel.repaint();
      }
    });
    myTextPanel.setLayout(new BorderLayout());
    myTextPanel.add(myEditor.getComponent(), BorderLayout.CENTER);

    final AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(getClass());
    String text = myCurrentFile == null ? settings.text : myInitText;
    try {
      myEditor.getDocument().setText(text);
      myEditor.getSelectionModel().setSelection(0, text.length());
    }
    finally {
      token.finish();
    }

    updateDialectsCombo(settings.dialect);
    updateExtensionsCombo();

    registerCustomKeyboardActions();

    final Dimension size = DimensionService.getInstance().getSize(getDimensionServiceKey(), myProject);
    if (size == null) {
      DimensionService.getInstance().setSize(getDimensionServiceKey(), new Dimension(800, 600));
    }
    myTextSplit.setDividerLocation(settings.textDividerLocation);
    myTreeSplit.setDividerLocation(settings.treeDividerLocation);
    myBlockRefSplitPane.setDividerLocation(settings.blockRefDividerLocation);

    updateEditor();
    super.init();
  }

  private static void initTree(JTree tree) {
    UIUtil.setLineStyleAngled(tree);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.updateUI();
    ToolTipManager.sharedInstance().registerComponent(tree);
    TreeUtil.installActions(tree);
    new TreeSpeedSearch(tree);
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.internal.psiView.PsiViewerDialog";
  }

  @Override
  protected String getHelpId() {
    return "reference.psi.viewer";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myEditor.getContentComponent();
  }

  private void registerCustomKeyboardActions() {
    final int mask = SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.ALT_DOWN_MASK;

    registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        focusEditor();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_T, mask));

    registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        focusTree();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_S, mask));


    registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        focusBlockTree();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_K, mask));

    registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        focusRefs();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_R, mask));

    registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myRefs.isFocusOwner()) {
          focusBlockTree();
        }
        else if (myPsiTree.isFocusOwner()) {
          focusRefs();
        }
        else if (myBlockTree.isFocusOwner()) {
          focusTree();
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
  }

  private void registerKeyboardAction(ActionListener actionListener, KeyStroke keyStroke) {
    getRootPane().registerKeyboardAction(actionListener, keyStroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  private void focusEditor() {
    IdeFocusManager.getInstance(myProject).requestFocus(myEditor.getContentComponent(), true);
  }

  private void focusTree() {
    IdeFocusManager.getInstance(myProject).requestFocus(myPsiTree, true);
  }

  private void focusRefs() {
    IdeFocusManager.getInstance(myProject).requestFocus(myRefs, true);
    if (myRefs.getModel().getSize() > 0) {
      if (myRefs.getSelectedIndex() == -1) {
        myRefs.setSelectedIndex(0);
      }
    }
  }

  private void focusBlockTree() {
    IdeFocusManager.getInstance(myProject).requestFocus(myBlockTree, true);
  }

  private void initMnemonics() {
    myTextSeparator.setLabelFor(myEditor.getContentComponent());
    myPsiTreeSeparator.setLabelFor(myPsiTree);
    myRefsSeparator.setLabelFor(myRefs);
    myBlockTreeSeparator.setLabelFor(myBlockTree);
  }

  private void updateIntersectHighlighter(int highlightStart, int highlightEnd) {
    if (myIntersectHighlighter != null) {
      myEditor.getMarkupModel().removeHighlighter(myIntersectHighlighter);
      myIntersectHighlighter.dispose();
    }
    if (myEditor.getSelectionModel().hasSelection()) {
      int selectionStart = myEditor.getSelectionModel().getSelectionStart();
      int selectionEnd = myEditor.getSelectionModel().getSelectionEnd();
      TextRange resRange = new TextRange(highlightStart, highlightEnd).intersection(new TextRange(selectionStart, selectionEnd));
      if (resRange != null) {
        TextAttributes attributes = new TextAttributes();
        attributes.setBackgroundColor(Color.LIGHT_GRAY);
        attributes.setForegroundColor(Color.white);
        myIntersectHighlighter = myEditor.getMarkupModel()
          .addRangeHighlighter(resRange.getStartOffset(), resRange.getEndOffset(), HighlighterLayer.LAST + 1, attributes,
                               HighlighterTargetArea.EXACT_RANGE);
      }
    }
  }

  @Nullable
  private PsiElement getPsiElement() {
    final TreePath path = myPsiTree.getSelectionPath();
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

  private void updateDialectsCombo(@Nullable final String lastUsed) {
    final Object source = getSource();
    if (source instanceof LanguageFileType) {
      final Language baseLang = ((LanguageFileType)source).getLanguage();
      final SortedComboBoxModel<Language> model = new SortedComboBoxModel<Language>(new DialectsComparator(baseLang));
      model.add(baseLang);
      model.addAll(LanguageUtil.getLanguageDialects(baseLang));
      myDialectComboBox.setModel(model);
    }
    else {
      myDialectComboBox.setModel(new DefaultComboBoxModel());
    }

    final int size = myDialectComboBox.getModel().getSize();
    final boolean visible = size > 1;
    myDialectLabel.setVisible(visible);
    myDialectComboBox.setVisible(visible);
    if (visible && (myCurrentFile != null || lastUsed != null)) {
      final SortedComboBoxModel model = (SortedComboBoxModel)myDialectComboBox.getModel();
      String curLanguage = myCurrentFile != null ? myCurrentFile.getLanguage().toString() : lastUsed;
      for (int i = 0; i < size; ++i) {
        if (curLanguage.equals(model.get(i).toString())) {
          myDialectComboBox.setSelectedIndex(i);
          return;
        }
      }
      myDialectComboBox.setSelectedIndex(size > 0 ? 0 : -1);
    }
  }

  private void updateExtensionsCombo() {
    final Object source = getSource();
    if (source instanceof LanguageFileType) {
      final List<String> extensions = getAllExtensions((LanguageFileType)source);
      if (extensions.size() > 1) {
        final ExtensionComparator comp = new ExtensionComparator(extensions.get(0));
        Collections.sort(extensions, comp);
        final SortedComboBoxModel<String> model = new SortedComboBoxModel<String>(comp);
        model.setAll(extensions);
        myExtensionComboBox.setModel(model);
        myExtensionComboBox.setVisible(true);
        myExtensionLabel.setVisible(true);
        String fileExt = myCurrentFile != null ? FileUtil.getExtension(myCurrentFile.getName()) : "";
        if (fileExt.length() > 0 && extensions.contains(fileExt)) {
          myExtensionComboBox.setSelectedItem(fileExt);
          return;
        }
        myExtensionComboBox.setSelectedIndex(0);
        return;
      }
    }
    myExtensionComboBox.setVisible(false);
    myExtensionLabel.setVisible(false);
  }

  private static final Pattern EXT_PATTERN = Pattern.compile("[a-z0-9]*");

  private static List<String> getAllExtensions(LanguageFileType fileType) {
    final List<FileNameMatcher> associations = FileTypeManager.getInstance().getAssociations(fileType);
    final List<String> extensions = new ArrayList<String>();
    extensions.add(fileType.getDefaultExtension().toLowerCase());
    for (FileNameMatcher matcher : associations) {
      final String presentableString = matcher.getPresentableString().toLowerCase();
      if (presentableString.startsWith("*.")) {
        final String ext = presentableString.substring(2);
        if (ext.length() > 0 && !extensions.contains(ext) && EXT_PATTERN.matcher(ext).matches()) {
          extensions.add(ext);
        }
      }
    }
    return extensions;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  private Object getSource() {
    final SourceWrapper wrapper = (SourceWrapper)myFileTypeComboBox.getSelectedItem();
    if (wrapper != null) {
      return wrapper.myFileType != null ? wrapper.myFileType : wrapper.myExtension;
    }
    return null;
  }

  @Override
  protected void doOKAction() {
    if (myBlockTreeBuilder != null) {
      Disposer.dispose(myBlockTreeBuilder);
    }
    final String text = myEditor.getDocument().getText();
    myEditor.getSelectionModel().removeSelection();

    myLastParsedText = text;
    myLastParsedTextHashCode = text.hashCode();
    myNewDocumentHashCode = myLastParsedTextHashCode;
    PsiElement rootElement = null;

    final Object source = getSource();
    try {
      if (source instanceof PsiViewerExtension) {
        final PsiViewerExtension ext = (PsiViewerExtension)source;
        rootElement = ext.createElement(myProject, text);
      }
      else if (source instanceof FileType) {
        final FileType type = (FileType)source;
        String ext = type.getDefaultExtension();
        if (myExtensionComboBox.isVisible()) {
          ext = myExtensionComboBox.getSelectedItem().toString().toLowerCase();
        }
        if (type instanceof LanguageFileType) {
          final Language language = ((LanguageFileType)type).getLanguage();
          final Language dialect = (Language)myDialectComboBox.getSelectedItem();
          rootElement = PsiFileFactory.getInstance(myProject).createFileFromText("Dummy." + ext, dialect == null ? language : dialect, text);
        }
        else {
          rootElement = PsiFileFactory.getInstance(myProject).createFileFromText("Dummy." + ext, text);
        }
      }
      focusTree();
    }
    catch (IncorrectOperationException e) {
      rootElement = null;
      Messages.showMessageDialog(myProject, e.getMessage(), "Error", Messages.getErrorIcon());
    }
    ViewerTreeStructure structure = (ViewerTreeStructure)myPsiTreeBuilder.getTreeStructure();
    structure.setRootPsiElement(rootElement);

    myPsiTreeBuilder.queueUpdate();
    myPsiTree.setRootVisible(true);
    myPsiTree.expandRow(0);
    myPsiTree.setRootVisible(false);

    Block rootBlock = rootElement == null ? null : buildBlocks(rootElement);
    if (rootBlock == null) {
      myBlockTreeBuilder = null;
      myBlockTree.setRootVisible(false);
      myBlockTree.setVisible(false);
      return;
    }

    myBlockTree.setVisible(true);
    BlockTreeStructure blockTreeStructure = new BlockTreeStructure();
    BlockTreeNode rootNode = new BlockTreeNode(rootBlock, null);
    blockTreeStructure.setRoot(rootNode);
    myBlockTreeBuilder = new BlockTreeBuilder(myBlockTree, blockTreeStructure);
    myPsiToBlockMap = new HashMap<PsiElement, BlockTreeNode>();
    final PsiElement psiFile = ((ViewerTreeStructure)myPsiTreeBuilder.getTreeStructure()).getRootPsiElement();
    initMap(rootNode, psiFile);
    PsiElement rootPsi = (rootNode.getBlock() instanceof ASTBlock) ?
                         ((ASTBlock)rootNode.getBlock()).getNode().getPsi() : rootElement;
    BlockTreeNode blockNode = myPsiToBlockMap.get(rootPsi);

    if (blockNode == null) {
      LOG.error(LogMessageEx
                  .createEvent("PsiViewer: rootNode not found", "Current language: " + rootElement.getContainingFile().getLanguage(),
                               new Attachment(rootElement.getContainingFile().getOriginalFile().getVirtualFile())));
      blockNode = findBlockNode(rootPsi);
    }

    blockTreeStructure.setRoot(blockNode);
    myBlockTree.addTreeSelectionListener(new MyBlockTreeSelectionListener());
    myBlockTree.setRootVisible(true);
    myBlockTree.expandRow(0);
    myBlockTreeBuilder.queueUpdate();
  }

  @Nullable
  private static Block buildBlocks(@NotNull PsiElement rootElement) {
    FormattingModelBuilder formattingModelBuilder = LanguageFormatting.INSTANCE.forContext(rootElement);
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(rootElement.getProject());
    if (formattingModelBuilder != null) {
      FormattingModel formattingModel = formattingModelBuilder.createModel(rootElement, settings);
      return formattingModel.getRootBlock();
    }
    else {
      return null;
    }
  }

  private void initMap(BlockTreeNode rootBlockNode, PsiElement psiEl) {
    PsiElement currentElem = null;
    if (rootBlockNode.getBlock() instanceof ASTBlock) {
      ASTNode node = ((ASTBlock)rootBlockNode.getBlock()).getNode();
      if (node != null) {
        currentElem = node.getPsi();
      }
    }
    if (currentElem == null) {
      currentElem =
        InjectedLanguageUtil.findElementAtNoCommit(psiEl.getContainingFile(), rootBlockNode.getBlock().getTextRange().getStartOffset());
    }
    myPsiToBlockMap.put(currentElem, rootBlockNode);

//nested PSI elements with same ranges will be mapped to one blockNode
    assert currentElem != null;
    TextRange curTextRange = currentElem.getTextRange();
    PsiElement parentElem = currentElem.getParent();
    while (parentElem != null && parentElem.getTextRange().equals(curTextRange)) {
      myPsiToBlockMap.put(parentElem, rootBlockNode);
      parentElem = parentElem.getParent();
    }
    for (BlockTreeNode block : rootBlockNode.getChildren()) {
      initMap(block, psiEl);
    }
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.NAVIGATABLE.is(dataId)) {
      String fqn = null;
      if (myPsiTree.hasFocus()) {
        final TreePath path = myPsiTree.getSelectionPath();
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

  private class MyPsiTreeSelectionListener implements TreeSelectionListener {
    private final TextAttributes myAttributes;

    public MyPsiTreeSelectionListener() {
      myAttributes = new TextAttributes();
      myAttributes.setBackgroundColor(SELECTION_BG_COLOR);
      myAttributes.setForegroundColor(Color.white);
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
      if (!myEditor.getDocument().getText().equals(myLastParsedText) || myBlockTree.hasFocus()) return;
      TreePath path = myPsiTree.getSelectionPath();
      clearSelection();
      if (path != null) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        if (!(node.getUserObject() instanceof ViewerNodeDescriptor)) return;
        ViewerNodeDescriptor descriptor = (ViewerNodeDescriptor)node.getUserObject();
        Object elementObject = descriptor.getElement();
        final PsiElement element = elementObject instanceof PsiElement
                                   ? (PsiElement)elementObject
                                   : elementObject instanceof ASTNode ? ((ASTNode)elementObject).getPsi() : null;
        if (element != null) {
          TextRange rangeInHostFile = InjectedLanguageManager.getInstance(myProject).injectedToHost(element, element.getTextRange());
          int start = rangeInHostFile.getStartOffset();
          int end = rangeInHostFile.getEndOffset();
          final ViewerTreeStructure treeStructure = (ViewerTreeStructure)myPsiTreeBuilder.getTreeStructure();
          PsiElement rootPsiElement = treeStructure.getRootPsiElement();
          if (rootPsiElement != null) {
            int baseOffset = rootPsiElement.getTextRange().getStartOffset();
            start -= baseOffset;
            end -= baseOffset;
          }
          final int textLength = myEditor.getDocument().getTextLength();
          if (end <= textLength) {
            myHighlighter = myEditor.getMarkupModel().addRangeHighlighter(start, end, HighlighterLayer.LAST, myAttributes, HighlighterTargetArea.EXACT_RANGE);
              updateIntersectHighlighter(start, end);

            if (myPsiTree.hasFocus()) {
              myEditor.getCaretModel().moveToOffset(start);
              myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
            }
          }
          if (myBlockTreeBuilder != null && myPsiTree.hasFocus()) {
            BlockTreeNode currentBlockNode = findBlockNode(element);
            if (currentBlockNode != null) {
              selectBlockNode(currentBlockNode);
            }
          }
          updateReferences(element);
        }
      }
    }
  }

  @Nullable
  private BlockTreeNode findBlockNode(PsiElement element) {
    BlockTreeNode result = myPsiToBlockMap.get(element);
    if (result == null) {
      TextRange rangeInHostFile = InjectedLanguageManager.getInstance(myProject).injectedToHost(element, element.getTextRange());
      result = findBlockNode(rangeInHostFile, true);
    }
    return result;
  }

  private void selectBlockNode(@Nullable BlockTreeNode currentBlockNode) {
    if (myBlockTreeBuilder == null) return;
    if (currentBlockNode != null) {
      myIgnoreBlockTreeSelectionMarker++;
      myBlockTreeBuilder.select(currentBlockNode, new Runnable() {
        @Override
        public void run() {
          // hope this is always called!
          assert myIgnoreBlockTreeSelectionMarker > 0;
          myIgnoreBlockTreeSelectionMarker--;
        }
      });
    }
    else {
      myIgnoreBlockTreeSelectionMarker++;
      try {
        myBlockTree.getSelectionModel().clearSelection();
      }
      finally {
        assert myIgnoreBlockTreeSelectionMarker > 0;
        myIgnoreBlockTreeSelectionMarker--;
      }
    }
  }

  private class MyBlockTreeSelectionListener implements TreeSelectionListener {
    private final TextAttributes myAttributes;

    public MyBlockTreeSelectionListener() {
      myAttributes = new TextAttributes();
      myAttributes.setBackgroundColor(SELECTION_BG_COLOR);
      myAttributes.setForegroundColor(Color.white);
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
      if (myIgnoreBlockTreeSelectionMarker > 0 || myBlockTreeBuilder == null) {
        return;
      }

      Set<?> blockElementsSet = myBlockTreeBuilder.getSelectedElements();
      if (blockElementsSet.isEmpty()) return;
      BlockTreeNode descriptor = (BlockTreeNode)blockElementsSet.iterator().next();
      PsiElement rootPsi = ((ViewerTreeStructure)myPsiTreeBuilder.getTreeStructure()).getRootPsiElement();
      int blockStart = descriptor.getBlock().getTextRange().getStartOffset();
      PsiElement currentPsiEl = InjectedLanguageUtil.findElementAtNoCommit(rootPsi.getContainingFile(), blockStart);
      int blockLength = descriptor.getBlock().getTextRange().getLength();
      while (currentPsiEl != null &&
             currentPsiEl.getTextRange().getStartOffset() == blockStart &&
             currentPsiEl.getTextLength() != blockLength) {
        currentPsiEl = currentPsiEl.getParent();
      }
      final BlockTreeStructure treeStructure = (BlockTreeStructure)myBlockTreeBuilder.getTreeStructure();
      BlockTreeNode rootBlockNode = treeStructure.getRootElement();
      int baseOffset = 0;
      if (rootBlockNode != null) {
        baseOffset = rootBlockNode.getBlock().getTextRange().getStartOffset();
      }
      if (currentPsiEl != null) {
        TextRange range = descriptor.getBlock().getTextRange();
        range = range.shiftRight(-baseOffset);
        int start = range.getStartOffset();
        int end = range.getEndOffset();
        final int textLength = myEditor.getDocument().getTextLength();

        if (myBlockTree.hasFocus()) {
          clearSelection();
          if (end <= textLength) {
            myHighlighter = myEditor.getMarkupModel()
              .addRangeHighlighter(start, end, HighlighterLayer.LAST, myAttributes, HighlighterTargetArea.EXACT_RANGE);
            updateIntersectHighlighter(start, end);

            myEditor.getCaretModel().moveToOffset(start);
            myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
          }
        }
        updateReferences(currentPsiEl);
        if (!myPsiTree.hasFocus()) {
          myPsiTreeBuilder.select(currentPsiEl);
        }
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
    if (myHighlighter != null) {
      myEditor.getMarkupModel().removeHighlighter(myHighlighter);
      myHighlighter.dispose();
    }
  }

  @Override
  public void doCancelAction() {
    final PsiViewerSettings settings = PsiViewerSettings.getSettings();
    final SourceWrapper wrapper = (SourceWrapper)myFileTypeComboBox.getSelectedItem();
    if (wrapper != null) settings.type = wrapper.getText();
    settings.text = myEditor.getDocument().getText();
    settings.showTreeNodes = myShowTreeNodesCheckBox.isSelected();
    settings.showWhiteSpaces = myShowWhiteSpacesBox.isSelected();
    final Object selectedDialect = myDialectComboBox.getSelectedItem();
    settings.dialect = myDialectComboBox.isVisible() && selectedDialect != null ? selectedDialect.toString() : "";
    settings.textDividerLocation = myTextSplit.getDividerLocation();
    settings.treeDividerLocation = myTreeSplit.getDividerLocation();
    settings.showBlocks = myShowBlocksCheckBox.isSelected();
    if( myShowBlocksCheckBox.isSelected()) {
         settings.blockRefDividerLocation = myBlockRefSplitPane.getDividerLocation();
    }
    super.doCancelAction();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myPsiTreeBuilder);
    if (myBlockTreeBuilder != null) {
      Disposer.dispose(myBlockTreeBuilder);
    }
    if (!myEditor.isDisposed()) {
      EditorFactory.getInstance().releaseEditor(myEditor);
    }
    super.dispose();
  }

  @Nullable
  private PsiElement resolve(int index) {
    final PsiElement element = getPsiElement();
    if (element == null) return null;
    @SuppressWarnings("unchecked")
    Map<PsiElement, PsiElement[]> map = (Map<PsiElement, PsiElement[]>)myRefs.getClientProperty(REFS_CACHE);
    if (map == null) {
      myRefs.putClientProperty(REFS_CACHE, map = new HashMap<PsiElement, PsiElement[]>());
    }
    PsiElement[] cache = map.get(element);
    if (cache == null) {
      final PsiReference[] references = element.getReferences();
      cache = new PsiElement[references.length];
      for (int i = 0; i < references.length; i++) {
        cache[i] = references[i].resolve();
      }
      map.put(element, cache);
    }
    return index >= cache.length ? null : cache[index];
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

  private class GoToListener implements KeyListener, MouseListener, ListSelectionListener {
    private RangeHighlighter myListenerHighlighter;
    private final TextAttributes myAttributes =
      new TextAttributes(Color.white, SELECTION_BG_COLOR, Color.red, EffectType.BOXED, Font.PLAIN);

    private void navigate() {
      final Object value = myRefs.getSelectedValue();
      if (value instanceof String) {
        final String fqn = (String)value;
        final PsiFile file = getContainingFileForClass(fqn);
        if (file != null) file.navigate(true);
      }
    }

    @Override
    public void keyPressed(KeyEvent e) {
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        navigate();
      }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() > 1) {
        navigate();
      }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
      clearSelection();
      updateDialectsCombo(null);
      updateExtensionsCombo();
      final int ind = myRefs.getSelectedIndex();
      final PsiElement element = getPsiElement();
      if (ind > -1 && element != null) {
        final PsiReference[] references = element.getReferences();
        if (ind < references.length) {
          final TextRange textRange = references[ind].getRangeInElement();
          TextRange range = InjectedLanguageManager.getInstance(myProject).injectedToHost(element, element.getTextRange());
          int start = range.getStartOffset();
          int end = range.getEndOffset();
          final ViewerTreeStructure treeStructure = (ViewerTreeStructure)myPsiTreeBuilder.getTreeStructure();
          PsiElement rootPsiElement = treeStructure.getRootPsiElement();
          if (rootPsiElement != null) {
            int baseOffset = rootPsiElement.getTextRange().getStartOffset();
            start -= baseOffset;
            end -= baseOffset;
          }

          start += textRange.getStartOffset();
          end = start + textRange.getLength();
          myListenerHighlighter = myEditor.getMarkupModel()
            .addRangeHighlighter(start, end, HighlighterLayer.FIRST + 1, myAttributes, HighlighterTargetArea.EXACT_RANGE);
        }
      }
    }

    public void clearSelection() {
      if (myListenerHighlighter != null &&
          ArrayUtil.contains(myListenerHighlighter, (Object[])myEditor.getMarkupModel().getAllHighlighters())) {
        myListenerHighlighter.dispose();
        myListenerHighlighter = null;
      }
    }

    @Override
    public void keyTyped(KeyEvent e) {}
    @Override
    public void keyReleased(KeyEvent e) {}
    @Override
    public void mousePressed(MouseEvent e) {}
    @Override
    public void mouseReleased(MouseEvent e) {}
    @Override
    public void mouseEntered(MouseEvent e) {}
    @Override
    public void mouseExited(MouseEvent e) {}
  }

  private void updateEditor() {
    final Object source = getSource();

    final String fileName = "Dummy." + (source instanceof FileType? ((FileType)source).getDefaultExtension() : "txt");
    final LightVirtualFile lightFile;
    if (source instanceof PsiViewerExtension) {
      lightFile = new LightVirtualFile(fileName, ((PsiViewerExtension)source).getDefaultFileType(), "");
    }
    else if (source instanceof LanguageFileType) {
      lightFile = new LightVirtualFile(fileName, ObjectUtils
        .chooseNotNull((Language)myDialectComboBox.getSelectedItem(), ((LanguageFileType)source).getLanguage()), "");
    }
    else if (source instanceof FileType) {
      lightFile = new LightVirtualFile(fileName, (FileType)source, "");
    }
    else {
      return;
    }
    myEditor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, lightFile));
  }

  private class EditorListener implements CaretListener, SelectionListener, DocumentListener {
    @Override
    public void caretPositionChanged(CaretEvent e) {
      if (!available() || myEditor.getSelectionModel().hasSelection()) return;
      final ViewerTreeStructure treeStructure = (ViewerTreeStructure)myPsiTreeBuilder.getTreeStructure();
      final PsiElement rootPsiElement = treeStructure.getRootPsiElement();
      if (rootPsiElement == null) return;
      final PsiElement rootElement = ((ViewerTreeStructure)myPsiTreeBuilder.getTreeStructure()).getRootPsiElement();
      int baseOffset = rootPsiElement.getTextRange().getStartOffset();
      final int offset = myEditor.getCaretModel().getOffset() + baseOffset;
      final PsiElement element = InjectedLanguageUtil.findElementAtNoCommit(rootElement.getContainingFile(), offset);
      if (element != null && myBlockTreeBuilder != null) {
        TextRange rangeInHostFile = InjectedLanguageManager.getInstance(myProject).injectedToHost(element, element.getTextRange());
        selectBlockNode(findBlockNode(rangeInHostFile, true));
      }
      myPsiTreeBuilder.select(element);
    }

    @Override
    public void selectionChanged(SelectionEvent e) {
      if (!available() || !myEditor.getSelectionModel().hasSelection()) return;
      final PsiElement rootElement =((ViewerTreeStructure)myPsiTreeBuilder.getTreeStructure()).getRootPsiElement();
      final SelectionModel selection = myEditor.getSelectionModel();
      final ViewerTreeStructure treeStructure = (ViewerTreeStructure)myPsiTreeBuilder.getTreeStructure();
      PsiElement rootPsiElement = treeStructure.getRootPsiElement();
      int baseOffset = rootPsiElement.getTextRange().getStartOffset();
      final int start = selection.getSelectionStart()+baseOffset;
      final int end = selection.getSelectionEnd()+baseOffset - 1;
      final PsiElement element =
        findCommonParent(InjectedLanguageUtil.findElementAtNoCommit(rootElement.getContainingFile(), start),
                         InjectedLanguageUtil.findElementAtNoCommit(rootElement.getContainingFile(), end));
      if (element != null  && myBlockTreeBuilder != null) {
        if (myEditor.getContentComponent().hasFocus()) {
          TextRange rangeInHostFile = InjectedLanguageManager.getInstance(myProject).injectedToHost(element, element.getTextRange());
          selectBlockNode(findBlockNode(rangeInHostFile, true));
          updateIntersectHighlighter(myHighlighter.getStartOffset(), myHighlighter.getEndOffset());
        }
      }
      myPsiTreeBuilder.select(element);
    }

    @Nullable
    private PsiElement findCommonParent(PsiElement start, PsiElement end) {
      if (end == null || start == end) {
        return start;
      }
      final TextRange endRange = end.getTextRange();
      PsiElement parent = start.getContext();
      while (parent != null && !parent.getTextRange().contains(endRange)) {
        parent = parent.getContext();
      }
      return parent;
    }

    private boolean available() {
      return myLastParsedTextHashCode == myNewDocumentHashCode && myEditor.getContentComponent().hasFocus();
    }

    @Nullable
    private PsiFile getPsiFile() {
      final PsiElement root = ((ViewerTreeStructure)myPsiTreeBuilder.getTreeStructure()).getRootPsiElement();
      return root instanceof PsiFile ? (PsiFile)root : null;
    }

    @Override
    public void beforeDocumentChange(DocumentEvent event) {

    }
    @Override
    public void documentChanged(DocumentEvent event) {
      myNewDocumentHashCode = event.getDocument().getText().hashCode();
    }
  }

  private static class AutoExpandFocusListener extends FocusAdapter {
    private final JComboBox myComboBox;
    private final Component myParent;

    private AutoExpandFocusListener(final JComboBox comboBox) {
      myComboBox = comboBox;
      myParent = UIUtil.findUltimateParent(myComboBox);
    }

    @Override
    public void focusGained(final FocusEvent e) {
      final Component from = e.getOppositeComponent();
      if (!e.isTemporary() && from != null && !myComboBox.isPopupVisible() && isUnder(from, myParent)) {
        myComboBox.setPopupVisible(true);
      }
    }

    private static boolean isUnder(Component component, final Component parent) {
      while (component != null) {
        if (component == parent) return true;
        component = component.getParent();
      }
      return false;
    }
  }

  @Nullable
  private BlockTreeNode findBlockNode(TextRange range, boolean selectParentIfNotFound) {
    if (myBlockTreeBuilder == null || !myBlockStructurePanel.isVisible()) {
      return null;
    }

    BlockTreeNode node = (BlockTreeNode)myBlockTreeBuilder.getTreeStructure().getRootElement();
    main_loop:
    while (true) {
      if (node.getBlock().getTextRange().equals(range)) {
        return node;
      }

      for (BlockTreeNode child : node.getChildren()) {
        if (child.getBlock().getTextRange().contains(range)) {
          node = child;
          continue main_loop;
        }
      }
      return selectParentIfNotFound ? node : null;
    }
  }
}
