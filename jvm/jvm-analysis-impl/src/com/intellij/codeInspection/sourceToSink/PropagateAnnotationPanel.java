// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.analysis.problemsView.toolWindow.ProblemsView;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.border.IdeaTitledBorder;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.BaseTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SingleEdtTaskScheduler;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
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
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class PropagateAnnotationPanel extends JPanel implements Disposable {
  private final Tree myTree;
  @NotNull
  private final Project myProject;
  private final List<TaintNode> myRoots;
  private final PropagateTreeListener myTreeSelectionListener;
  private final @NotNull Consumer<? super Collection<@NotNull TaintNode>> myCallback;
  private Content myContent;
  private final boolean mySupportRefactoring;

  PropagateAnnotationPanel(@NotNull Project project,
                           @NotNull List<TaintNode> roots,
                           @NotNull Consumer<? super Collection<@NotNull TaintNode>> callback,
                           boolean supportRefactoring) {
    super(new BorderLayout());
    myTree = PropagateTree.create(this, roots);
    myRoots = roots;
    myProject = project;
    myCallback = callback;
    mySupportRefactoring = supportRefactoring;

    Editor usageEditor = createEditor();
    Editor memberEditor = createEditor();
    myTreeSelectionListener = new PropagateTreeListener(usageEditor, memberEditor);
    myTree.getSelectionModel().addTreeSelectionListener(myTreeSelectionListener);

    Splitter splitter = new Splitter(false, .6f);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    splitter.setFirstComponent(scrollPane);

    JPanel panel = new JPanel(new BorderLayout());
    JComponent callSitesViewer = createCallSitesViewer(usageEditor, memberEditor);
    panel.add(callSitesViewer);
    myTreeSelectionListener.updateEditorTexts(roots.get(0));
    JPanel toolbar = createToolbar();
    panel.add(toolbar, BorderLayout.NORTH);
    splitter.setSecondComponent(panel);

    add(splitter, BorderLayout.CENTER);
    addTreeActions(myTree, roots);
  }

  public void setContent(@NotNull Content content) {
    myContent = content;
    Disposer.register(content, this);
  }

  private @NotNull JPanel createToolbar() {
    JPanel panel = new JPanel(new BorderLayout());
    if (mySupportRefactoring) {
      String annotateText = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.toolwindow.annotate");
      JButton annotateButton = new JButton(annotateText);
      annotateButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          ToolWindow toolWindow = ProblemsView.getToolWindow(myProject);
          if (toolWindow == null) return;
          ContentManager contentManager = toolWindow.getContentManager();
          contentManager.removeContent(myContent, true);
          myContent.release();
          for (TaintNode root : myRoots) {
            Set<TaintNode> toAnnotate = getSelectedElements(root, new HashSet<>());
            if (toAnnotate != null) myCallback.accept(toAnnotate);
          }
        }
      });
      panel.add(annotateButton, BorderLayout.WEST);
    }
    return panel;
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

  static @Nullable Set<TaintNode> getSelectedElements(@NotNull TaintNode taintNode,
                                                      @NotNull Set<TaintNode> nodes) {
    if (taintNode.getPsiElement() == null) return null;
    if (taintNode.isExcluded()) return nodes;
    nodes.add(taintNode);
    List<TaintNode> children = taintNode.calcChildren();
    if (children == null) return nodes;
    for (TaintNode child : children) {
      if (getSelectedElements(child, nodes) == null) return null;
    }
    return nodes;
  }

  private static @NotNull JComponent createCallSitesViewer(@NotNull Editor usageEditor, @NotNull Editor memberEditor) {
    Splitter splitter = new Splitter(true);
    JComponent usageComponent = getEditorComponent(usageEditor, JvmAnalysisBundle.message("propagated.from"));
    splitter.setFirstComponent(usageComponent);
    JComponent memberComponent = getEditorComponent(memberEditor, JvmAnalysisBundle.message("propagated.to"));
    splitter.setSecondComponent(memberComponent);
    splitter.setBorder(IdeBorderFactory.createRoundedBorder());
    return splitter;
  }

  @NotNull
  private static JComponent getEditorComponent(@NotNull Editor editor, @NlsContexts.BorderTitle String title) {
    EditorSettings memberEditorSettings = editor.getSettings();
    memberEditorSettings.setGutterIconsShown(false);
    memberEditorSettings.setLineNumbersShown(false);
    JComponent memberComponent = editor.getComponent();
    IdeaTitledBorder memberTitleBorder = IdeBorderFactory.createTitledBorder(title, false);
    memberTitleBorder.setShowLine(false);
    memberComponent.setBorder(memberTitleBorder);
    return memberComponent;
  }

  private void addTreeActions(@NotNull Tree tree, @NotNull List<TaintNode> roots) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    for (TaintNode root : roots) {
      if (root.myTaintValue != TaintValue.TAINTED && mySupportRefactoring) {
        actionGroup.addAll(createIncludeExcludeActions(tree));
      }
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
        if (selectionPaths == null || selectionPaths.length == 0) return;
        TaintNode[] roots = tree.getSelectedNodes(TaintNode.class, null);
        Deque<TaintNode> nodes = new ArrayDeque<>(Arrays.asList(roots));
        boolean enable = false;
        while (!nodes.isEmpty()) {
          TaintNode node = nodes.poll();
          if (node.myTaintValue == TaintValue.TAINTED) {
            e.getPresentation().setEnabled(false);
            return;
          }
          enable |= node.isExcluded() != myExclude;
          List<TaintNode> children = node.myCachedChildren;
          if (children != null) nodes.addAll(children);
        }
        e.getPresentation().setEnabled(enable);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        // getSelectedNodes used
        return ActionUpdateThread.EDT;
      }

      private static Stream<TaintNode> nodes(TaintNode[] roots) {
        return StreamEx.of(roots)
          .flatMap(root -> StreamEx.ofTree(root, n -> n.myCachedChildren == null ? null : StreamEx.of(n.myCachedChildren)));
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        TaintNode[] roots = tree.getSelectedNodes(TaintNode.class, null);
        nodes(roots).forEach(n -> {
          n.setExcluded(myExclude);
          n.update();
        });
      }
    }

    return new AnAction[]{
      new IncludeExcludeAction(JavaRefactoringBundle.message("type.migration.include.action.text"), false),
      new IncludeExcludeAction(JavaRefactoringBundle.message("type.migration.exclude.action.text"), true)
    };
  }

  private static final class PropagateTreeListener implements TreeSelectionListener, Disposable {
    private final ElementEditor myUsageEditor;
    private final ElementEditor myMemberEditor;
    private final SingleEdtTaskScheduler alarm = SingleEdtTaskScheduler.createSingleEdtTaskScheduler();

    private PropagateTreeListener(@NotNull Editor usageEditor,
                                  @NotNull Editor memberEditor) {
      myUsageEditor = new ElementEditor(usageEditor);
      myMemberEditor = new ElementEditor(memberEditor);
    }

    @Override
    public void valueChanged(@NotNull TreeSelectionEvent e) {
      TreePath path = e.getPath();
      if (path == null) return;
      TaintNode taintNode = (TaintNode)path.getLastPathComponent();
      alarm.cancelAndRequest(300, () -> updateEditorTexts(taintNode));
    }

    void updateEditorTexts(@NotNull TaintNode taintNode) {
      //clear all
      myUsageEditor.show(null, null);
      myMemberEditor.show(null, null);

      PsiElement usage = taintNode.getRef();
      if (usage == null) return;
      usage = MarkAsSafeFix.getSourcePsi(usage);
      if (usage == null) return;
      PsiElement parentPsi = getParentPsi(usage);
      if (parentPsi == null) return;
      PsiElement usageHighlight = PsiTreeUtil.isAncestor(parentPsi, usage, true) ? usage : getIdentifier(parentPsi);
      if (usageHighlight == null) return;
      myUsageEditor.show(parentPsi, usageHighlight);

      PsiElement element = taintNode.getPsiElement();
      if (element == null) return;
      element = MarkAsSafeFix.getSourcePsi(element);
      if (element == null) return;
      PsiElement elementHighlight = getIdentifier(element);
      if (elementHighlight == null) return;
      myMemberEditor.show(element, elementHighlight);
    }

    @Override
    public void dispose() {
      alarm.dispose();
      Disposer.dispose(myUsageEditor);
      Disposer.dispose(myMemberEditor);
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

    private static class ElementEditor implements Disposable {

      private final Editor myEditor;
      private final Collection<RangeHighlighter> myHighlighters = new ArrayList<>();

      private ElementEditor(Editor editor) {
        myEditor = editor;
      }

      public void show(@Nullable PsiElement element, @Nullable PsiElement toHighlight) {
        if (element == null || toHighlight == null) {
          ApplicationManager.getApplication().runWriteAction(() -> myEditor.getDocument().setText(""));
          return;
        }
        ElementModel model = ElementModel.create(element, toHighlight);
        if (model == null) return;
        String text = model.myText;
        ApplicationManager.getApplication().runWriteAction(() -> myEditor.getDocument().setText(text));
        EditorHighlighter highlighter = createHighlighter(element);
        ((EditorEx)myEditor).setHighlighter(highlighter);
        myEditor.getCaretModel().moveToOffset(model.myHighlightRange.getStartOffset());
        myEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
        highlightRange(element.getProject(), model.myHighlightRange);
      }

      private void highlightRange(@NotNull Project project, @NotNull TextRange range) {
        HighlightManager highlighter = HighlightManager.getInstance(project);
        myHighlighters.forEach(h -> highlighter.removeSegmentHighlighter(myEditor, h));
        myHighlighters.clear();
        highlighter.addRangeHighlight(myEditor, range.getStartOffset(), range.getEndOffset(),
                                      EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES, false, myHighlighters);
      }

      @Override
      public void dispose() {
        EditorFactory.getInstance().releaseEditor(myEditor);
      }

      private static @NotNull EditorHighlighter createHighlighter(@NotNull PsiElement element) {
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        Project project = element.getProject();
        VirtualFile virtualFile = element.getContainingFile().getVirtualFile();
        return HighlighterFactory.createHighlighter(virtualFile, scheme, project);
      }

      private static class ElementModel {
        private final String myText;
        private final TextRange myHighlightRange;

        private ElementModel(@NotNull String text, @NotNull TextRange highlightRange) {
          myText = text;
          myHighlightRange = highlightRange;
        }

        static @Nullable ElementModel create(@NotNull PsiElement element, @NotNull PsiElement toHighlight) {
          PsiFile file = element.getContainingFile();
          if (file == null) return null;
          Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(file);
          if (document == null) return null;
          TextRange textRange = element.getTextRange();
          if (textRange == null) return null;
          int startLineNumber = document.getLineNumber(textRange.getStartOffset());
          int start = document.getLineStartOffset(startLineNumber);
          int end = document.getLineEndOffset(document.getLineNumber(textRange.getEndOffset()));
          String[] lines = document.getText(TextRange.create(start, end)).split("\n");
          int indent = getIndent(lines);
          TextRange highlightRange = toHighlight.getTextRange();
          int highlightLine = document.getLineNumber(highlightRange.getStartOffset()) - startLineNumber;
          int highlightIndent = 0;
          for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) continue;
            if (i <= highlightLine) highlightIndent += indent;
            lines[i] = line.substring(indent);
          }
          highlightRange = highlightRange.shiftLeft(start).shiftLeft(highlightIndent);
          return new ElementModel(StringUtil.join(lines, "\n"), highlightRange);
        }

        private static int getIndent(String @NotNull [] lines) {
          int prefix = Integer.MAX_VALUE;
          for (int i = 0; i < lines.length && prefix != 0; i++) {
            String line = lines[i];
            int indent = 0;
            while (indent < line.length() && Character.isWhitespace(line.charAt(indent))) indent++;
            if (indent == line.length()) continue;
            if (indent < prefix) prefix = indent;
          }
          return prefix;
        }
      }
    }
  }

  private static final class PropagateTree extends Tree implements UiDataProvider {
    private PropagateTree(TreeModel treeModel) {
      super(treeModel);
      getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      TaintNode[] selectedNodes = getSelectedNodes(TaintNode.class, null);
      sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> {
        return selectedNodes.length == 1 ? selectedNodes[0].getRef() : null;
      });
    }

    @Contract("_, _ -> new")
    private static @NotNull PropagateTree create(@NotNull Disposable parent, @NotNull List<TaintNode> roots) {
      TaintNode rootWrapper = new TaintNode(null, null, null, null, true) {
        @Override
        public List<TaintNode> calcChildren() {
          return roots;
        }
      };
      BaseTreeModel<TaintNode> propagateTreeModel = new PropagateTreeModel(rootWrapper);
      AsyncTreeModel treeModel = new AsyncTreeModel(propagateTreeModel, parent);
      PropagateTree tree = new PropagateTree(treeModel);
      tree.setRootVisible(false);
      tree.setShowsRootHandles(true);
      TreeUtil.installActions(tree);
      EditSourceOnDoubleClickHandler.install(tree);
      return tree;
    }

    private static class PropagateTreeModel extends BaseTreeModel<TaintNode> implements InvokerSupplier {

      private final TaintNode myRootWrapper;

      private PropagateTreeModel(TaintNode wrapper) {
        myRootWrapper = wrapper;
      }

      @Override
      public List<? extends TaintNode> getChildren(Object object) {
        TaintNode node = ObjectUtils.tryCast(object, TaintNode.class);
        if (node == null) return Collections.emptyList();
        return node.calcChildren();
      }

      @Override
      public TaintNode getRoot() {
        return myRootWrapper;
      }

      @Override
      public @NotNull Invoker getInvoker() {
        return Invoker.forBackgroundThreadWithReadAction(this);
      }
    }
  }
}
