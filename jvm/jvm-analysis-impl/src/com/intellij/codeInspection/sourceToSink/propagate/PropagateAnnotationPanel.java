// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink.propagate;

import com.intellij.CommonBundle;
import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInspection.sourceToSink.TaintValue;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.ide.util.PsiMethodRenderingInfo;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewContentManager;
import com.intellij.util.Alarm;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UField;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class PropagateAnnotationPanel extends JPanel implements Disposable {

  private final Tree myTree;
  private final Project myProject;
  private final TaintNode myRoot;
  private final PropagateTreeListener myTreeSelectionListener;
  private final Consumer<@NotNull Collection<PsiModifierListOwner>> myCallback;
  private Content myContent;

  PropagateAnnotationPanel(Project project, @NotNull TaintNode root, @NotNull Consumer<Collection<@NotNull PsiModifierListOwner>> callback) {
    super(new BorderLayout());
    myTree = PropagateTree.create(root);
    myRoot = root;
    myProject = project;
    myCallback = callback;
    Editor usageEditor = createEditor();
    Editor memberEditor = createEditor();
    myTreeSelectionListener = new PropagateTreeListener(usageEditor, memberEditor, myRoot);
    myTree.getSelectionModel().addTreeSelectionListener(myTreeSelectionListener);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    Splitter splitter = new Splitter(false, (float)0.6);
    splitter.setFirstComponent(scrollPane);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        splitter.dispose();
      }
    });
    JComponent callSitesViewer = createCallSitesViewer(usageEditor, memberEditor);
    TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath == null) {
      selectionPath = new TreePath(myRoot.getPath());
      myTree.getSelectionModel().addSelectionPath(selectionPath);
    }

    TaintNode node = (TaintNode)selectionPath.getLastPathComponent();
    myTreeSelectionListener.updateEditorTexts(node);

    splitter.setSecondComponent(callSitesViewer);
    add(splitter);
    addTreeActions(myTree, root);
    addToolbar(root);
  }

  public void setContent(@NotNull Content content) {
    myContent = content;
    Disposer.register(content, this);
  }

  private void addToolbar(@NotNull TaintNode root) {
    if (root.myTaintValue == TaintValue.TAINTED) return;
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 1, GridBagConstraints.NORTHWEST,
                                                   GridBagConstraints.NONE, JBUI.insets(5, 10, 5, 0), 0, 0);
    String annotateText = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.toolwindow.annotate");
    JButton annotateButton = new JButton(annotateText);
    annotateButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        UsageViewContentManager.getInstance(myProject).closeContent(myContent);
        Set<PsiModifierListOwner> toAnnotate = getSelectedElements();
        if (toAnnotate != null) myCallback.accept(toAnnotate);
      }
    });
    panel.add(annotateButton, gc);
    JButton closeButton = new JButton(CommonBundle.getCancelButtonText());
    closeButton.addActionListener(e -> UsageViewContentManager.getInstance(myProject).closeContent(myContent));
    panel.add(closeButton, gc);
    add(panel, BorderLayout.SOUTH);
  }

  public Tree getTree() {
    return myTree;
  }

  private @NotNull Editor createEditor() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document document = editorFactory.createDocument("");
    return editorFactory.createViewer(document, myProject);
  }

  @Override
  public void dispose() {
    if (myTree != null) {
      Disposer.dispose(myTreeSelectionListener);
      myTree.removeTreeSelectionListener(myTreeSelectionListener);
    }
  }

  public @Nullable Set<PsiModifierListOwner> getSelectedElements() {
    return getSelectedElements(myRoot, myRoot, new HashSet<>());
  }

  static @Nullable Set<PsiModifierListOwner> getSelectedElements(@NotNull TaintNode root,
                                                                 @NotNull TaintNode taintNode,
                                                                 @NotNull Set<PsiModifierListOwner> elements) {
    PsiModifierListOwner element = taintNode.getElement();
    if (element == null) return null;
    if (taintNode.isExcluded()) return elements;
    if (taintNode != root) elements.add(element);
    if (taintNode.myChildren == null) return elements;
    for (TaintNode child : taintNode.myChildren) {
      if (getSelectedElements(root, child, elements) == null) return null;
    }
    return elements;
  }

  private static @NotNull JComponent createCallSitesViewer(@NotNull Editor usageEditor, @NotNull Editor memberEditor) {
    Splitter splitter = new Splitter(true);
    JComponent usageComponent = usageEditor.getComponent();
    usageComponent.setBorder(IdeBorderFactory.createTitledBorder(JvmAnalysisBundle.message("propagated.from"), false));
    splitter.setFirstComponent(usageComponent);
    JComponent memberComponent = memberEditor.getComponent();
    memberComponent.setBorder(IdeBorderFactory.createTitledBorder(JvmAnalysisBundle.message("propagated.to"), false));
    splitter.setSecondComponent(memberComponent);
    splitter.setBorder(IdeBorderFactory.createRoundedBorder());
    return splitter;
  }

  private static void addTreeActions(@NotNull Tree tree, @NotNull TaintNode root) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    if (root.myTaintValue != TaintValue.TAINTED) {
      actionGroup.addAll(createIncludeExcludeActions(tree));
    }
    actionGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    PopupHandler.installPopupMenu(tree, actionGroup, "PropagateAnnotationPanelPopup");
  }

  @Contract("_ -> new")
  private static AnAction @NotNull [] createIncludeExcludeActions(@NotNull Tree tree) {
    class IncludeExcludeAction extends AnAction {

      private final boolean myExclude;

      private IncludeExcludeAction(@NlsActions.ActionText String name, boolean exclude) {
        super(name);
        myExclude = exclude;
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        TreePath[] selectionPaths = tree.getSelectionPaths();
        if (selectionPaths == null || selectionPaths.length <= 0) return;
        TaintNode[] roots = tree.getSelectedNodes(TaintNode.class, null);
        e.getPresentation().setEnabled(nodes(roots).anyMatch(n -> n.isExcluded() != myExclude));
      }

      private Stream<TaintNode> nodes(TaintNode[] roots) {
        return StreamEx.of(roots)
          .flatMap(root -> StreamEx.ofTree(root, n -> StreamEx.of(n.myChildren == null ? Collections.emptySet() : n.myChildren)));
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        TaintNode[] roots = tree.getSelectedNodes(TaintNode.class, null);
        nodes(roots).forEach(n -> n.setExcluded(myExclude));
        tree.repaint();
      }
    }

    return new AnAction[]{
      new IncludeExcludeAction(JavaRefactoringBundle.message("type.migration.include.action.text"), false),
      new IncludeExcludeAction(JavaRefactoringBundle.message("type.migration.exclude.action.text"), true)
    };
  }

  private static class PropagateTreeListener implements TreeSelectionListener, Disposable {

    private final ElementEditor myUsageEditor;
    private final ElementEditor myMemberEditor;
    private final TaintNode myRoot;
    private final Alarm myAlarm = new Alarm();
    
    private PropagateTreeListener(@NotNull Editor usageEditor,
                                  @NotNull Editor memberEditor,
                                  @NotNull TaintNode root) {
      myUsageEditor = new ElementEditor(usageEditor, "propagate.from.empty.text");
      myMemberEditor = new ElementEditor(memberEditor, "propagate.to.empty.text");
      myRoot = root;
    }

    @Override
    public void valueChanged(@NotNull TreeSelectionEvent e) {
      TreePath path = e.getPath();
      if (path == null) return;
      TaintNode taintNode = (TaintNode)path.getLastPathComponent();
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(() -> updateEditorTexts(taintNode), 300);
    }
    
    void updateEditorTexts(@NotNull TaintNode taintNode) {
      if (taintNode == myRoot || taintNode.getParent() == null) {
        myUsageEditor.show(null);
        myMemberEditor.show(null);
        return;
      }
      PsiElement usage = taintNode.getRef();
      if (usage == null) return;
      PsiElement parentPsi = getParentPsi(usage);
      if (parentPsi == null) return;
      myUsageEditor.show(parentPsi);
      PsiModifierListOwner element = taintNode.getElement();
      if (element == null) return;
      myMemberEditor.show(element);
      PsiElement usageHighlight = PsiTreeUtil.isAncestor(parentPsi, usage, true) ? usage : getIdentifier(parentPsi);
      if (usageHighlight != null) myUsageEditor.highlight(parentPsi, usageHighlight);
      PsiElement elementHighlight = getIdentifier(element);
      if (elementHighlight != null) myMemberEditor.highlight(element, elementHighlight);
    }

    @Override
    public void dispose() {
      Disposer.dispose(myUsageEditor);
      Disposer.dispose(myMemberEditor);
    }

    private static class ElementEditor implements Disposable {
      
      private final Editor myEditor;
      private final Collection<RangeHighlighter> myHighlighters = new ArrayList<>();
      private final String myEmptyText;


      private ElementEditor(Editor editor, String emptyText) { 
        myEditor = editor;
        myEmptyText = emptyText;
      }
      
      public void show(@Nullable PsiElement element) {
        String elementText = getText(element);
        String text = elementText == null ? JvmAnalysisBundle.message(myEmptyText) : elementText;
        ApplicationManager.getApplication().runWriteAction(() -> myEditor.getDocument().setText(text));
        if (element == null) return;
        EditorHighlighter highlighter = createHighlighter(element);
        ((EditorEx)myEditor).setHighlighter(highlighter);
      }
      
      public void highlight(@NotNull PsiElement elementInEditor, @NotNull PsiElement toHighlight) {
        Project project = elementInEditor.getProject();
        HighlightManager highlighter = HighlightManager.getInstance(project);
        myHighlighters.forEach(h -> highlighter.removeSegmentHighlighter(myEditor, h));
        myHighlighters.clear();
        TextRange range = rangeInParent(elementInEditor, toHighlight);
        if (range == null) return;
        highlighter.addRangeHighlight(myEditor, range.getStartOffset(), range.getEndOffset(), 
                                      EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES, false, myHighlighters);
      }

      @Override
      public void dispose() {
        EditorFactory.getInstance().releaseEditor(myEditor);
      }

      private static @Nullable TextRange rangeInParent(@NotNull PsiElement parent, @NotNull PsiElement child) {
        int parentStart = getStartOffset(parent);
        if (parentStart == -1) return null;
        TextRange childRange = child.getTextRange();
        return childRange.shiftLeft(parentStart);
      }

      private static int getStartOffset(@NotNull PsiElement owner) {
        PsiFile file = owner.getContainingFile();
        Document document = PsiDocumentManager.getInstance(owner.getProject()).getDocument(file);
        if (document == null) return -1;
        int lineNumber = document.getLineNumber(owner.getTextRange().getStartOffset());
        return document.getLineStartOffset(lineNumber);
      }

      private static @Nullable String getText(@Nullable PsiElement element) {
        if (element == null) return null;
        PsiFile file = element.getContainingFile();
        if (file == null) return null;
        Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(file);
        if (document == null) return null;
        TextRange textRange = element.getTextRange();
        if (textRange == null) return null;
        int start = document.getLineStartOffset(document.getLineNumber(textRange.getStartOffset()));
        int end = document.getLineEndOffset(document.getLineNumber(textRange.getEndOffset()));
        return document.getText().substring(start, end);
      }

      private static @NotNull EditorHighlighter createHighlighter(@NotNull PsiElement element) {
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        Project project = element.getProject();
        VirtualFile virtualFile = element.getContainingFile().getVirtualFile();
        return HighlighterFactory.createHighlighter(virtualFile, scheme, project);
      }
    }

    private static @Nullable PsiElement getIdentifier(@Nullable PsiElement element) {
      PsiNameIdentifierOwner namedElement = ObjectUtils.tryCast(element, PsiNameIdentifierOwner.class);
      if (namedElement == null) return null;
      return namedElement.getNameIdentifier();
    }

    private static @Nullable PsiElement getParentPsi(@NotNull PsiElement usage) {
      @SuppressWarnings("unchecked")
      Class<? extends UElement>[] types = new Class[]{UMethod.class, UField.class};
      UElement uElement = UastContextKt.getUastParentOfTypes(usage, types);
      if (uElement == null) return null;
      return uElement.getSourcePsi();
    }
  }

  private static class PropagateTree extends Tree implements DataProvider {
    private PropagateTree(TaintNode root) {
      super(root);
      this.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      this.getSelectionModel().setSelectionPath(new TreePath(root.getPath()));
    }

    @Override
    public @Nullable Object getData(@NotNull String dataId) {
      if (!CommonDataKeys.PSI_ELEMENT.is(dataId)) return null;
      DefaultMutableTreeNode[] selectedNodes = getSelectedNodes(DefaultMutableTreeNode.class, null);
      if (selectedNodes.length != 1) return null;
      return selectedNodes[0].getUserObject();
    }

    @Contract("_ -> new")
    private static @NotNull PropagateTree create(@NotNull TaintNode root) {
      TaintNode rootWrapper = new TaintNode();
      rootWrapper.add(root);
      PropagateTree tree = new PropagateTree(rootWrapper);
      tree.setRootVisible(false);
      tree.setShowsRootHandles(true);
      tree.setCellRenderer(new PropagateTree.PropagateTreeRenderer());
      TreeUtil.installActions(tree);
      TreeUtil.expand(tree, 2);
      SmartExpander.installOn(tree);
      EditSourceOnDoubleClickHandler.install(tree);
      new TreeSpeedSearch(tree);
      return tree;
    }

    private static class PropagateTreeRenderer extends ColoredTreeCellRenderer {

      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        TaintNode taintNode = ObjectUtils.tryCast(value, TaintNode.class);
        if (taintNode == null) return;
        PsiModifierListOwner owner = taintNode.getElement();
        if (owner == null) {
          append(UsageViewBundle.message("node.invalid"), SimpleTextAttributes.ERROR_ATTRIBUTES);
          return;
        }
        int flags = Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS;
        setIcon(ReadAction.compute(() -> owner.getIcon(flags)));
        int style = taintNode.isExcluded() ? SimpleTextAttributes.STYLE_STRIKEOUT : SimpleTextAttributes.STYLE_PLAIN;
        SimpleTextAttributes attributes = new SimpleTextAttributes(style, null);
        PsiMethod psiMethod = ObjectUtils.tryCast(owner, PsiMethod.class);
        // TODO: kotlin methods?
        if (psiMethod != null) {
          PsiMethodRenderingInfo renderingInfo = new PsiMethodRenderingInfo(true);
          append(renderingInfo.getPresentableText(psiMethod), attributes);
          return;
        }
        PsiVariable psiVariable = ObjectUtils.tryCast(owner, PsiVariable.class);
        if (psiVariable != null) {
          String varText = PsiFormatUtil.formatVariable(psiVariable,
                                                        PsiFormatUtilBase.SHOW_NAME |
                                                        PsiFormatUtilBase.SHOW_TYPE |
                                                        PsiFormatUtilBase.TYPE_AFTER,
                                                        PsiSubstitutor.EMPTY);
          append(varText, attributes);
          return;
        }
        PsiNamedElement namedElement = ObjectUtils.tryCast(owner, PsiNamedElement.class);
        if (namedElement == null) return;
        String name = namedElement.getName();
        if (name == null) return;
        append(name, attributes);
      }
    }
  }
}
