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
package com.intellij.internal.psiView;

import com.intellij.diagnostic.AttachmentFactory;
import com.intellij.diagnostic.LogMessageEx;
import com.intellij.formatting.ASTBlock;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ide.util.treeView.TreeVisitor;
import com.intellij.internal.psiView.formattingblocks.BlockTreeBuilder;
import com.intellij.internal.psiView.formattingblocks.BlockTreeNode;
import com.intellij.internal.psiView.formattingblocks.BlockTreeStructure;
import com.intellij.internal.psiView.stubtree.PsiViewerStubTreeBuilder;
import com.intellij.internal.psiView.stubtree.StubTreeNode;
import com.intellij.internal.psiView.stubtree.StubTreeStructure;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.indexing.FileContentImpl;
import com.intellij.util.indexing.IndexingDataKeys;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
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
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

/**
 * @author Konstantin Bulenkov
 */
public class PsiViewerDialog extends DialogWrapper implements DataProvider, Disposable {
  private static final String REFS_CACHE = "References Resolve Cache";
  private static final Color BOX_COLOR = new JBColor(new Color(0xFC6C00), new Color(0xDE6C01));
  private static final Logger LOG = Logger.getInstance("#com.intellij.internal.psiView.PsiViewerDialog");
  private final Project myProject;

  private JPanel myPanel;
  private JComboBox<PsiViewerSourceWrapper> myFileTypeComboBox;
  private JCheckBox myShowWhiteSpacesBox;
  private JCheckBox myShowTreeNodesCheckBox;
  private JBLabel myDialectLabel;
  private JComboBox<Language> myDialectComboBox;
  private JLabel myExtensionLabel;
  private JComboBox<String> myExtensionComboBox;
  private JPanel myTextPanel;
  private JSplitPane myTextSplit;
  private JSplitPane myTreeSplit;
  private Tree myPsiTree;
  private ViewerTreeBuilder myPsiTreeBuilder;
  private JList myRefs;

  private Tree myBlockTree;
  private JPanel myBlockStructurePanel;
  private JCheckBox myShowBlocksCheckBox;
  private TitledSeparator myTextSeparator;
  private TitledSeparator myPsiTreeSeparator;
  private Tree myStubTree;
  @Nullable
  private BlockTreeBuilder myBlockTreeBuilder;
  @Nullable
  private PsiViewerStubTreeBuilder myPsiViewerStubTreeBuilder;
  private RangeHighlighter myHighlighter;
  private HashMap<PsiElement, BlockTreeNode> myPsiToBlockMap;

  private final Set<PsiViewerSourceWrapper> mySourceWrappers = ContainerUtil.newTreeSet();
  private final EditorEx myEditor;
  private final EditorListener myEditorListener = new EditorListener();
  private String myLastParsedText = null;
  private int myLastParsedTextHashCode = 17;
  private int myNewDocumentHashCode = 11;

  private int myIgnoreBlockTreeSelectionMarker = 0;

  private boolean myExternalDocument;

  @NotNull
  private final JBTabsImpl myTabs;

  private void createUIComponents() {
    myPsiTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
  }


  private static class ExtensionComparator implements Comparator<String> {
    private final String myOnTop;

    public ExtensionComparator(String onTop) {
      myOnTop = onTop;
    }

    @Override
    public int compare(@NotNull String o1, @NotNull String o2) {
      if (o1.equals(myOnTop)) return -1;
      if (o2.equals(myOnTop)) return 1;
      return o1.compareToIgnoreCase(o2);
    }
  }

  public PsiViewerDialog(@NotNull Project project, @Nullable Editor selectedEditor) {
    super(project, true);
    myProject = project;
    myExternalDocument = selectedEditor != null;
    myTabs = createTabPanel(project);
    myBlockStructurePanel = new JPanel(new BorderLayout());
    myRefs = new JBList(new DefaultListModel());
    myStubTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    myBlockTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));

    setOKButtonText("&Build PSI Tree");
    setCancelButtonText("&Close");
    Disposer.register(myProject, getDisposable());
    VirtualFile selectedFile = selectedEditor == null ? null : FileDocumentManager.getInstance().getFile(selectedEditor.getDocument());
    setTitle(selectedFile == null ? "PSI Viewer" : "PSI Viewer: " + selectedFile.getName());
    if (selectedEditor != null) {
      myEditor = (EditorEx)EditorFactory.getInstance().createEditor(selectedEditor.getDocument(), myProject);
    }
    else {
      PsiViewerSettings settings = PsiViewerSettings.getSettings();
      Document document = EditorFactory.getInstance().createDocument(StringUtil.notNullize(settings.text));
      myEditor = (EditorEx)EditorFactory.getInstance().createEditor(document, myProject);
      myEditor.getSelectionModel().setSelection(0, document.getTextLength());
    }
    myEditor.getSettings().setLineMarkerAreaShown(false);
    init();
    if (selectedEditor != null) {
      doOKAction();

      ApplicationManager.getApplication().invokeLater(() -> {
        getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(myEditor.getContentComponent(), true));
        myEditor.getCaretModel().moveToOffset(selectedEditor.getCaretModel().getOffset());
        myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        //myEditor.getSelectionModel().setSelection(selectedEditor.getSelectionModel().getSelectionStart(),
        //                                          selectedEditor.getSelectionModel().getSelectionEnd());
      }, ModalityState.stateForComponent(myPanel));
    }
  }

  @NotNull
  private static JBEditorTabs createTabPanel(@NotNull Project project) {
    return new JBEditorTabs(project, ActionManager.getInstance(), IdeFocusManager.getInstance(project), project) {
      @Override
      public boolean isAlphabeticalMode() {
        return false;
      }

      @Override
      public boolean supportsCompression() {
        return false;
      }

      @Override
      protected Color getEmptySpaceColor() {
        return UIUtil.getBgFillColor(getParent());
      }

      @Override
      protected void paintSelectionAndBorder(Graphics2D g2d) {
        super.paintSelectionAndBorder(g2d);
      }
    };
  }

  @NotNull
  private static JScrollPane createScrollPanel(@NotNull JComponent element) {
    return ScrollPaneFactory.createScrollPane(element);
  }

  @Override
  protected void init() {
    initMnemonics();

    initTree(myPsiTree);
    final TreeCellRenderer renderer = myPsiTree.getCellRenderer();
    myPsiTree.setCellRenderer(new TreeCellRenderer() {
      @Override
      public Component getTreeCellRendererComponent(@NotNull JTree tree,
                                                    Object value,
                                                    boolean selected,
                                                    boolean expanded,
                                                    boolean leaf,
                                                    int row,
                                                    boolean hasFocus) {
        final Component c = renderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        if (value instanceof DefaultMutableTreeNode) {
          final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
          if (userObject instanceof ViewerNodeDescriptor) {
            final Object element = ((ViewerNodeDescriptor)userObject).getElement();
            if (c instanceof NodeRenderer) {
              ((NodeRenderer)c).setToolTipText(element == null ? null : element.getClass().getName());
            }
            if (element instanceof PsiElement && FileContextUtil.getFileContext(((PsiElement)element).getContainingFile()) != null ||
                element instanceof ViewerTreeStructure.Inject) {
              final TextAttributes attr =
                EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.INJECTED_LANGUAGE_FRAGMENT);
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

    JPanel panelWrapper = new JPanel(new BorderLayout());
    panelWrapper.add(myTabs.getComponent());
    myTreeSplit.add(panelWrapper, JSplitPane.RIGHT);

    myBlockStructurePanel.add(createScrollPanel(myBlockTree));
    myBlockStructurePanel.setBorder(IdeBorderFactory.createBorder());

    JPanel stubStructurePanel = new JPanel(new BorderLayout());
    stubStructurePanel.add(createScrollPanel(myStubTree));
    stubStructurePanel.setBorder(IdeBorderFactory.createBorder());

    JPanel referencesPanel = new JPanel(new BorderLayout());
    referencesPanel.add(myRefs);
    referencesPanel.setBorder(IdeBorderFactory.createBorder());

    myTabs.addTab(new TabInfo(referencesPanel).setText("References"));
    myTabs.addTab(new TabInfo(myBlockStructurePanel).setText("Block Structure"));
    myTabs.addTab(new TabInfo(stubStructurePanel).setText("Stub Structure"));
    PsiViewerSettings settings = PsiViewerSettings.getSettings();
    int tabIndex = settings.lastSelectedTabIndex;
    TabInfo defaultInfo = tabIndex < myTabs.getTabCount() ? myTabs.getTabAt(tabIndex) : null;
    if (defaultInfo != null) {
      myTabs.select(defaultInfo, false);
    }
    myTabs.setSelectionChangeHandler((tab, focus, el) -> {
      settings.lastSelectedTabIndex = myTabs.getIndexOf(tab);
      return el.run();
    });

    final GoToListener listener = new GoToListener();
    myRefs.addKeyListener(listener);
    myRefs.addMouseListener(listener);
    myRefs.getSelectionModel().addListSelectionListener(listener);
    myRefs.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(@NotNull JList list,
                                                    Object value,
                                                    int index,
                                                    boolean isSelected,
                                                    boolean cellHasFocus) {
        final Component comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        try {
          if (resolve(index) == null) {
            comp.setForeground(JBColor.RED);
          }
        }
        catch (IndexNotReadyException ignore) {
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
      public Component getInitialComponent(@NotNull Window window) {
        return myEditor.getComponent();
      }
    });
    VirtualFile file = myExternalDocument ? FileDocumentManager.getInstance().getFile(myEditor.getDocument()) : null;
    Language curLanguage = LanguageUtil.getLanguageForPsi(myProject, file);

    String type = curLanguage != null ? curLanguage.getDisplayName() : settings.type;
    PsiViewerSourceWrapper lastUsed = null;
    mySourceWrappers.addAll(PsiViewerSourceWrapper.getExtensionBasedWrappers());

    List<PsiViewerSourceWrapper> fileTypeBasedWrappers = PsiViewerSourceWrapper.getFileTypeBasedWrappers();
    for (PsiViewerSourceWrapper wrapper : fileTypeBasedWrappers) {
      mySourceWrappers.addAll(fileTypeBasedWrappers);
      if (lastUsed == null && wrapper.getText().equals(type)) lastUsed = wrapper;
      if (curLanguage != null && wrapper.myFileType == curLanguage.getAssociatedFileType()) {
        lastUsed = wrapper;
      }
    }

    myFileTypeComboBox.setModel(new CollectionComboBoxModel<>(ContainerUtil.newArrayList(mySourceWrappers), lastUsed));
    myFileTypeComboBox.setRenderer(new ListCellRendererWrapper<PsiViewerSourceWrapper>() {
      @Override
      public void customize(JList list, PsiViewerSourceWrapper value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
          setText(value.getText());
          setIcon(value.getIcon());
        }
      }
    });
    new ComboboxSpeedSearch(myFileTypeComboBox) {
      @Override
      protected String getElementText(Object element) {
        return element instanceof PsiViewerSourceWrapper ? ((PsiViewerSourceWrapper)element).getText() : null;
      }
    };
    myFileTypeComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        updateDialectsCombo(null);
        updateExtensionsCombo();
        updateEditor();
      }
    });
    myDialectComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        updateEditor();
      }
    });
    new ComboboxSpeedSearch(myDialectComboBox) {
      @Override
      protected String getElementText(Object element) {
        return element instanceof Language ? ((Language)element).getDisplayName() : "<default>";
      }
    };
    myFileTypeComboBox.addFocusListener(new AutoExpandFocusListener(myFileTypeComboBox));
    if (!myExternalDocument && lastUsed == null && mySourceWrappers.size() > 0) {
      myFileTypeComboBox.setSelectedIndex(0);
    }

    myDialectComboBox.setRenderer(new ListCellRendererWrapper<Language>() {
      @Override
      public void customize(final JList list, final Language value, final int index, final boolean selected, final boolean hasFocus) {
        setText(value != null ? value.getDisplayName() : "<default>");
      }
    });
    myDialectComboBox.addFocusListener(new AutoExpandFocusListener(myDialectComboBox));
    myExtensionComboBox.setRenderer(new ListCellRendererWrapper<String>() {
      @Override
      public void customize(JList list, String value, int index, boolean selected, boolean hasFocus) {
        if (value != null) setText("." + value);
      }
    });
    myExtensionComboBox.addFocusListener(new AutoExpandFocusListener(myExtensionComboBox));

    final ViewerTreeStructure psiTreeStructure = getTreeStructure();
    myShowWhiteSpacesBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        psiTreeStructure.setShowWhiteSpaces(myShowWhiteSpacesBox.isSelected());
        myPsiTreeBuilder.queueUpdate();
      }
    });
    myShowTreeNodesCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        psiTreeStructure.setShowTreeNodes(myShowTreeNodesCheckBox.isSelected());
        myPsiTreeBuilder.queueUpdate();
      }
    });
    myShowWhiteSpacesBox.setSelected(settings.showWhiteSpaces);
    psiTreeStructure.setShowWhiteSpaces(settings.showWhiteSpaces);
    myShowTreeNodesCheckBox.setSelected(settings.showTreeNodes);
    psiTreeStructure.setShowTreeNodes(settings.showTreeNodes);
    myBlockStructurePanel.setVisible(settings.showBlocks);
    myTextPanel.setLayout(new BorderLayout());
    myTextPanel.add(myEditor.getComponent(), BorderLayout.CENTER);

    updateDialectsCombo(settings.dialect);
    updateExtensionsCombo();

    registerCustomKeyboardActions();

    final Dimension size = DimensionService.getInstance().getSize(getDimensionServiceKey(), myProject);
    if (size == null) {
      DimensionService.getInstance().setSize(getDimensionServiceKey(), JBUI.size(800, 600));
    }
    myTextSplit.setDividerLocation(settings.textDividerLocation);
    myTreeSplit.setDividerLocation(settings.treeDividerLocation);

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
  @NotNull
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
      public void actionPerformed(@NotNull ActionEvent e) {
        focusEditor();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_T, mask));

    registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        focusTree();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_S, mask));


    registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        focusBlockTree();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_K, mask));

    registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        focusRefs();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_R, mask));

    registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
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
    ArrayList<Language> items = new ArrayList<>();
    if (source instanceof LanguageFileType) {
      final Language baseLang = ((LanguageFileType)source).getLanguage();
      items.add(baseLang);
      Language[] dialects = LanguageUtil.getLanguageDialects(baseLang);
      Arrays.sort(dialects, LanguageUtil.LANGUAGE_COMPARATOR);
      items.addAll(Arrays.asList(dialects));
    }
    myDialectComboBox.setModel(new CollectionComboBoxModel<>(items));

    boolean visible = items.size() > 1;
    myDialectLabel.setVisible(visible);
    myDialectComboBox.setVisible(visible);
    if (visible && (myExternalDocument || lastUsed != null)) {
      VirtualFile file = myExternalDocument ? FileDocumentManager.getInstance().getFile(myEditor.getDocument()) : null;
      Language curLanguage = LanguageUtil.getLanguageForPsi(myProject, file);
      int idx = items.indexOf(curLanguage);
      myDialectComboBox.setSelectedIndex(idx >= 0 ? idx : 0);
    }
  }

  private void updateExtensionsCombo() {
    final Object source = getSource();
    if (source instanceof LanguageFileType) {
      List<String> extensions = getAllExtensions((LanguageFileType)source);
      if (extensions.size() > 1) {
        ExtensionComparator comp = new ExtensionComparator(extensions.get(0));
        Collections.sort(extensions, comp);
        SortedComboBoxModel<String> model = new SortedComboBoxModel<>(comp);
        model.setAll(extensions);
        myExtensionComboBox.setModel(model);
        myExtensionComboBox.setVisible(true);
        myExtensionLabel.setVisible(true);
        VirtualFile file = myExternalDocument ? FileDocumentManager.getInstance().getFile(myEditor.getDocument()) : null;
        String fileExt = file == null ? "" : FileUtilRt.getExtension(file.getName());
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
    final List<String> extensions = new ArrayList<>();
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
    final PsiViewerSourceWrapper wrapper = (PsiViewerSourceWrapper)myFileTypeComboBox.getSelectedItem();
    if (wrapper != null) {
      return wrapper.myFileType != null ? wrapper.myFileType : wrapper.myExtension;
    }
    return null;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    AbstractAction copyPsi = new AbstractAction("Cop&y PSI") {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        PsiElement element = parseText(myEditor.getDocument().getText());
        List<PsiElement> allToParse = new ArrayList<>();
        if (element instanceof PsiFile) {
          allToParse.addAll(((PsiFile)element).getViewProvider().getAllFiles());
        }
        else if (element != null) {
          allToParse.add(element);
        }
        StringBuilder data = new StringBuilder();
        for (PsiElement psiElement : allToParse) {
          data.append(DebugUtil.psiToString(psiElement, !myShowWhiteSpacesBox.isSelected(), true));
        }
        CopyPasteManager.getInstance().setContents(new StringSelection(data.toString()));
      }
    };
    return ArrayUtil.mergeArrays(new Action[]{copyPsi}, super.createActions());
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
    PsiElement rootElement = parseText(text);
    focusTree();
    ViewerTreeStructure structure = getTreeStructure();
    structure.setRootPsiElement(rootElement);

    myPsiTreeBuilder.queueUpdate();
    myPsiTree.setRootVisible(true);
    myPsiTree.expandRow(0);
    myPsiTree.setRootVisible(false);

    if (!myShowBlocksCheckBox.isSelected()) {
      return;
    }

    buildStubTree(rootElement, text);
    buildBlockTree(rootElement);
  }

  private void buildStubTree(PsiElement rootElement, @NotNull String textToParse) {
    if (!(rootElement instanceof PsiFileWithStubSupport)) {
      myStubTree.setRootVisible(false);
      myStubTree.removeAll();
      StatusText text = myStubTree.getEmptyText();
      if (rootElement instanceof PsiFile) {
        text.setText("No stubs for " + rootElement.getLanguage().getDisplayName());
      }
      else {
        text.setText("Cannot build stub tree for code fragments");
      }
      return;
    }
    Stub stub = buildStubForElement(myProject, rootElement, textToParse);
    
    if (stub instanceof StubElement) {
      final StubTreeNode rootNode = new StubTreeNode((StubElement)stub, null);
      final StubTreeStructure treeStructure = new StubTreeStructure(rootNode);
      myPsiViewerStubTreeBuilder = new PsiViewerStubTreeBuilder(myStubTree, treeStructure);
      myStubTree.setRootVisible(true);
      myStubTree.expandRow(0);
      myPsiViewerStubTreeBuilder.queueUpdate();
    }
    else {
      myStubTree.setRootVisible(false);
      myStubTree.removeAll();
      StatusText text = myStubTree.getEmptyText();
      text.setText("No stubs for " + rootElement.getLanguage().getDisplayName());
    }
  }

  @Nullable
  private static Stub buildStubForElement(Project project, PsiElement rootElement, @NotNull String textToParse) {
    Stub stub = null;
    StubTree tree = ((PsiFileWithStubSupport)rootElement).getStubTree();
    if (tree != null) {
      stub = tree.getRoot();
    }
    else if (rootElement instanceof PsiFileImpl) {
      IStubFileElementType builder = ((PsiFileImpl)rootElement).getElementTypeForStubBuilder();
      stub = builder == null ? null : builder.getBuilder().buildStubTree((PsiFile)rootElement);
    }
    if (stub == null) {
      LightVirtualFile file = new LightVirtualFile("stub", rootElement.getLanguage(), textToParse);
      final FileContentImpl fc;
      try {
        fc = new FileContentImpl(file, file.contentsToByteArray());
        fc.putUserData(IndexingDataKeys.PROJECT, project);
        stub = StubTreeBuilder.buildStubTree(fc);
      }
      catch (IOException e) {

      }
    }
    return stub;
  }

  private void buildBlockTree(PsiElement rootElement) {
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
    myPsiToBlockMap = new HashMap<>();
    final PsiElement psiFile = (getTreeStructure()).getRootPsiElement();
    initMap(rootNode, psiFile);
    PsiElement rootPsi = rootNode.getBlock() instanceof ASTBlock ?
                         ((ASTBlock)rootNode.getBlock()).getNode().getPsi() : rootElement;
    BlockTreeNode blockNode = myPsiToBlockMap.get(rootPsi);

    if (blockNode == null) {
      LOG.error(LogMessageEx
                  .createEvent("PsiViewer: rootNode not found", "Current language: " + rootElement.getContainingFile().getLanguage(),
                               AttachmentFactory.createAttachment(rootElement.getContainingFile().getOriginalFile().getVirtualFile())));
      blockNode = findBlockNode(rootPsi);
    }

    blockTreeStructure.setRoot(blockNode);
    myBlockTree.addTreeSelectionListener(new MyBlockTreeSelectionListener());
    myBlockTree.setRootVisible(true);
    myBlockTree.expandRow(0);
    myBlockTreeBuilder.queueUpdate();
  }

  @NotNull
  private ViewerTreeStructure getTreeStructure() {
    return ObjectUtils.notNull((ViewerTreeStructure)myPsiTreeBuilder.getTreeStructure());
  }

  private PsiElement parseText(String text) {
    final Object source = getSource();
    try {
      if (source instanceof PsiViewerExtension) {
        return ((PsiViewerExtension)source).createElement(myProject, text);
      }
      if (source instanceof FileType) {
        final FileType type = (FileType)source;
        String ext = type.getDefaultExtension();
        if (myExtensionComboBox.isVisible()) {
          ext = myExtensionComboBox.getSelectedItem().toString().toLowerCase(Locale.ENGLISH);
        }
        if (type instanceof LanguageFileType) {
          final Language dialect = (Language)myDialectComboBox.getSelectedItem();
          if (dialect != null) {
            return PsiFileFactory.getInstance(myProject).createFileFromText("Dummy." + ext, dialect, text);
          }
        }
        return PsiFileFactory.getInstance(myProject).createFileFromText("Dummy." + ext, type, text);
      }
    }
    catch (IncorrectOperationException e) {
      Messages.showMessageDialog(myProject, e.getMessage(), "Error", Messages.getErrorIcon());
    }
    return null;
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
    JBTreeTraverser<BlockTreeNode> traverser = new JBTreeTraverser<>(o -> JBIterable.of(o.getChildren()));
    for (BlockTreeNode block : traverser.withRoot(rootBlockNode)) {
      PsiElement currentElem = null;
      if (block.getBlock() instanceof ASTBlock) {
        ASTNode node = ((ASTBlock)block.getBlock()).getNode();
        if (node != null) {
          currentElem = node.getPsi();
        }
      }
      if (currentElem == null) {
        currentElem =
          InjectedLanguageUtil
            .findElementAtNoCommit(psiEl.getContainingFile(), block.getBlock().getTextRange().getStartOffset());
      }
      myPsiToBlockMap.put(currentElem, block);

      //nested PSI elements with same ranges will be mapped to one blockNode
      //    assert currentElem != null;      //for Scala-language plugin etc it can be null, because formatterBlocks is not instance of ASTBlock
      TextRange curTextRange = currentElem.getTextRange();
      PsiElement parentElem = currentElem.getParent();
      while (parentElem != null && parentElem.getTextRange().equals(curTextRange)) {
        myPsiToBlockMap.put(parentElem, block);
        parentElem = parentElem.getParent();
      }
    }
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
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
      }
      else if (myRefs.hasFocus()) {
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
      myAttributes.setEffectColor(BOX_COLOR);
      myAttributes.setEffectType(EffectType.ROUNDED_BOX);
    }

    @Override
    public void valueChanged(@NotNull TreeSelectionEvent e) {
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
          final ViewerTreeStructure treeStructure = getTreeStructure();
          PsiElement rootPsiElement = treeStructure.getRootPsiElement();
          if (rootPsiElement != null) {
            int baseOffset = rootPsiElement.getTextRange().getStartOffset();
            start -= baseOffset;
            end -= baseOffset;
          }
          final int textLength = myEditor.getDocument().getTextLength();
          if (end <= textLength) {
            myHighlighter = myEditor.getMarkupModel()
              .addRangeHighlighter(start, end, HighlighterLayer.LAST, myAttributes, HighlighterTargetArea.EXACT_RANGE);
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
          if (myPsiViewerStubTreeBuilder != null) {
            selectStubNode(element);
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
      myBlockTreeBuilder.select(currentBlockNode, () -> {
        // hope this is always called!
        assert myIgnoreBlockTreeSelectionMarker > 0;
        myIgnoreBlockTreeSelectionMarker--;
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

  private void selectStubNode(@Nullable PsiElement element) {
    if (myPsiViewerStubTreeBuilder == null || element == null) return;
    final PsiFile file = element.getContainingFile();
    if (!(file instanceof PsiFileWithStubSupport)) return;

    final PsiFileWithStubSupport stubFile = (PsiFileWithStubSupport)file;
    final StubTreeNode rootNode = (StubTreeNode)myPsiViewerStubTreeBuilder.getRootElement();
    if (rootNode == null) return;

    final StubElement<?> stub = rootNode.getStub();
    if (!(stub instanceof PsiFileStub)) return;

    final StubTree stubTree = new StubTree((PsiFileStub)stub);
    final TextRange elementTextRange = element.getTextRange();

    myPsiViewerStubTreeBuilder.select(StubTreeNode.class, new TreeVisitor<StubTreeNode>() {
      @Override
      public boolean visit(@NotNull StubTreeNode node) {
        final ASTNode stub = stubFile.findTreeForStub(stubTree, node.getStub());
        return stub != null && stub.getTextRange().equals(elementTextRange);
      }
    }, null, false);
  }

  private class MyBlockTreeSelectionListener implements TreeSelectionListener {
    private final TextAttributes myAttributes;

    public MyBlockTreeSelectionListener() {
      myAttributes = new TextAttributes();
      myAttributes.setEffectColor(BOX_COLOR);
      myAttributes.setEffectType(EffectType.ROUNDED_BOX);
    }

    @Override
    public void valueChanged(@NotNull TreeSelectionEvent e) {
      if (myIgnoreBlockTreeSelectionMarker > 0 || myBlockTreeBuilder == null) {
        return;
      }

      Set<?> blockElementsSet = myBlockTreeBuilder.getSelectedElements();
      if (blockElementsSet.isEmpty()) return;
      BlockTreeNode descriptor = (BlockTreeNode)blockElementsSet.iterator().next();
      PsiElement rootPsi = (getTreeStructure()).getRootPsiElement();
      int blockStart = descriptor.getBlock().getTextRange().getStartOffset();
      PsiFile file = rootPsi.getContainingFile();
      PsiElement currentPsiEl = InjectedLanguageUtil.findElementAtNoCommit(file, blockStart);
      if (currentPsiEl == null) currentPsiEl = file;
      int blockLength = descriptor.getBlock().getTextRange().getLength();
      while (currentPsiEl.getParent() != null &&
             currentPsiEl.getTextRange().getStartOffset() == blockStart &&
             currentPsiEl.getTextLength() != blockLength) {
        currentPsiEl = currentPsiEl.getParent();
      }
      final BlockTreeStructure treeStructure = ObjectUtils.notNull((BlockTreeStructure)myBlockTreeBuilder.getTreeStructure());
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
    }
    else {
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
    super.doCancelAction();
    PsiViewerSettings settings = PsiViewerSettings.getSettings();
    PsiViewerSourceWrapper wrapper = (PsiViewerSourceWrapper)myFileTypeComboBox.getSelectedItem();
    if (wrapper != null) settings.type = wrapper.getText();
    if (!myExternalDocument) {
      settings.text = StringUtil.first(myEditor.getDocument().getText(), 2048, true);
    }
    settings.showTreeNodes = myShowTreeNodesCheckBox.isSelected();
    settings.showWhiteSpaces = myShowWhiteSpacesBox.isSelected();
    Object selectedDialect = myDialectComboBox.getSelectedItem();
    settings.dialect = myDialectComboBox.isVisible() && selectedDialect != null ? selectedDialect.toString() : "";
    settings.textDividerLocation = myTextSplit.getDividerLocation();
    settings.treeDividerLocation = myTreeSplit.getDividerLocation();
    settings.showBlocks = myShowBlocksCheckBox.isSelected();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myPsiTreeBuilder);
    Disposer.dispose(myTabs);
    if (myBlockTreeBuilder != null) {
      Disposer.dispose(myBlockTreeBuilder);
    }
    if (!myEditor.isDisposed()) {
      EditorFactory.getInstance().releaseEditor(myEditor);
    }
    if (myPsiViewerStubTreeBuilder != null) {
      Disposer.dispose(myPsiViewerStubTreeBuilder);
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
      myRefs.putClientProperty(REFS_CACHE, map = new HashMap<>());
    }
    PsiElement[] cache = map.get(element);
    if (cache == null) {
      final PsiReference[] references = element.getReferences();
      cache = new PsiElement[references.length];
      for (int i = 0; i < references.length; i++) {
        final PsiReference reference = references[i];
        final PsiElement resolveResult;
        if (reference instanceof PsiPolyVariantReference) {
          final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(true);
          resolveResult = results.length == 0 ? null : results[0].getElement();
        }
        else {
          resolveResult = reference.resolve();
        }
        cache[i] = resolveResult;
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
    return ArrayUtil.getFirstElement(files);
  }

  @Nullable
  public static TreeNode findNodeWithObject(final Object object, final TreeModel model, final Object parent) {
    for (int i = 0; i < model.getChildCount(parent); i++) {
      final DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)model.getChild(parent, i);
      if (childNode.getUserObject().equals(object)) {
        return childNode;
      }
      else {
        final TreeNode node = findNodeWithObject(object, model, childNode);
        if (node != null) return node;
      }
    }
    return null;
  }

  private class GoToListener implements KeyListener, MouseListener, ListSelectionListener {
    private RangeHighlighter myListenerHighlighter;
    private final TextAttributes myAttributes =
      new TextAttributes(JBColor.RED, null, null, null, Font.PLAIN);

    private void navigate() {
      final Object value = myRefs.getSelectedValue();
      if (value instanceof String) {
        final String fqn = (String)value;
        final PsiFile file = getContainingFileForClass(fqn);
        if (file != null) file.navigate(true);
      }
    }

    @Override
    public void keyPressed(@NotNull KeyEvent e) {
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        navigate();
      }
    }

    @Override
    public void mouseClicked(@NotNull MouseEvent e) {
      if (e.getClickCount() > 1) {
        navigate();
      }
    }

    @Override
    public void valueChanged(@NotNull ListSelectionEvent e) {
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
          final ViewerTreeStructure treeStructure = getTreeStructure();
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
    public void keyTyped(@NotNull KeyEvent e) {}

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void mousePressed(@NotNull MouseEvent e) {}

    @Override
    public void mouseReleased(@NotNull MouseEvent e) {}

    @Override
    public void mouseEntered(@NotNull MouseEvent e) {}

    @Override
    public void mouseExited(@NotNull MouseEvent e) {}
  }

  private void updateEditor() {
    final Object source = getSource();

    final String fileName = "Dummy." + (source instanceof FileType ? ((FileType)source).getDefaultExtension() : "txt");
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
    EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, lightFile);
    try {
      myEditor.setHighlighter(highlighter);
    }
    catch (Throwable e) {
      LOG.warn(e);
    }
  }

  private class EditorListener implements SelectionListener, DocumentListener, CaretListener {
    @Override
    public void caretPositionChanged(CaretEvent e) {
      if (!available() || myEditor.getSelectionModel().hasSelection()) return;
      final ViewerTreeStructure treeStructure = getTreeStructure();
      final PsiElement rootPsiElement = treeStructure.getRootPsiElement();
      if (rootPsiElement == null) return;
      final PsiElement rootElement = (getTreeStructure()).getRootPsiElement();
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
      ViewerTreeStructure treeStructure = getTreeStructure();
      final PsiElement rootElement = treeStructure.getRootPsiElement();
      if (rootElement == null) return;
      final SelectionModel selection = myEditor.getSelectionModel();
      final TextRange textRange = rootElement.getTextRange();
      int baseOffset = textRange != null ? textRange.getStartOffset() : 0;
      final int start = selection.getSelectionStart() + baseOffset;
      final int end = selection.getSelectionEnd() + baseOffset - 1;
      final PsiElement element =
        findCommonParent(InjectedLanguageUtil.findElementAtNoCommit(rootElement.getContainingFile(), start),
                         InjectedLanguageUtil.findElementAtNoCommit(rootElement.getContainingFile(), end));
      if (element != null && myBlockTreeBuilder != null) {
        if (myEditor.getContentComponent().hasFocus()) {
          TextRange rangeInHostFile = InjectedLanguageManager.getInstance(myProject).injectedToHost(element, element.getTextRange());
          selectBlockNode(findBlockNode(rangeInHostFile, true));
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
    public void focusGained(@NotNull final FocusEvent e) {
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
    final BlockTreeBuilder builder = myBlockTreeBuilder;
    if (builder == null || !myBlockStructurePanel.isVisible()) {
      return null;
    }

    AbstractTreeStructure treeStructure = builder.getTreeStructure();
    if (treeStructure == null) return null;
    BlockTreeNode node = (BlockTreeNode)treeStructure.getRootElement();
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
