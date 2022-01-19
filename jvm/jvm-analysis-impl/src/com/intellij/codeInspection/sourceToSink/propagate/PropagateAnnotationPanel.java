// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sourceToSink.propagate;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInspection.sourceToSink.TaintValue;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.ide.util.PsiClassRenderingInfo;
import com.intellij.ide.util.PsiElementRenderingInfo;
import com.intellij.ide.util.PsiMethodRenderingInfo;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.*;
import com.intellij.ui.border.IdeaTitledBorder;
import com.intellij.ui.content.Content;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.BaseTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewContentManager;
import com.intellij.util.Alarm;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
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

public class PropagateAnnotationPanel extends JPanel implements Disposable {

  private final Tree myTree;
  private final Project myProject;
  private final TaintNode myRoot;
  private final PropagateTreeListener myTreeSelectionListener;
  private final Consumer<@NotNull Collection<TaintNode>> myCallback;
  private Content myContent;

  PropagateAnnotationPanel(Project project, @NotNull TaintNode root, @NotNull Consumer<Collection<@NotNull TaintNode>> callback) {
    super(new BorderLayout());
    myTree = PropagateTree.create(this, root);
    myRoot = root;
    myProject = project;
    myCallback = callback;

    Editor usageEditor = createEditor();
    Editor memberEditor = createEditor();
    myTreeSelectionListener = new PropagateTreeListener(usageEditor, memberEditor, myRoot);
    myTree.getSelectionModel().addTreeSelectionListener(myTreeSelectionListener);

    Splitter splitter = new Splitter(false, .6f);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    splitter.setFirstComponent(scrollPane);

    JPanel panel = new JPanel(new BorderLayout());
    JComponent callSitesViewer = createCallSitesViewer(usageEditor, memberEditor);
    panel.add(callSitesViewer);
    myTreeSelectionListener.updateEditorTexts(root);
    JPanel toolbar = createToolbar();
    panel.add(toolbar, BorderLayout.NORTH);
    splitter.setSecondComponent(panel);

    add(splitter, BorderLayout.CENTER);
    addTreeActions(myTree, root);
  }

  public void setContent(@NotNull Content content) {
    myContent = content;
    Disposer.register(content, this);
  }

  private @NotNull JPanel createToolbar() {
    JPanel panel = new JPanel(new BorderLayout());
    String annotateText = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.toolwindow.annotate");
    JButton annotateButton = new JButton(annotateText);
    annotateButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        UsageViewContentManager.getInstance(myProject).closeContent(myContent);
        Set<TaintNode> toAnnotate = getSelectedElements(myRoot, new HashSet<>());
        if (toAnnotate != null) myCallback.accept(toAnnotate);
      }
    });
    panel.add(annotateButton, BorderLayout.WEST);
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

      private Stream<TaintNode> nodes(TaintNode[] roots) {
        return StreamEx.of(roots)
          .flatMap(
            root -> StreamEx.ofTree(root, n -> StreamEx.of(n.myCachedChildren == null ? Collections.emptyList() : n.myCachedChildren)));
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
      if (taintNode == myRoot || taintNode.getParentDescriptor() == null) {
        myUsageEditor.show(null, null);
        myMemberEditor.show(null, null);
        return;
      }
      PsiElement usage = taintNode.getRef();
      if (usage == null) return;
      PsiElement parentPsi = getParentPsi(usage);
      if (parentPsi == null) return;
      PsiElement usageHighlight = PsiTreeUtil.isAncestor(parentPsi, usage, true) ? usage : getIdentifier(parentPsi);
      if (usageHighlight == null) return;
      myUsageEditor.show(parentPsi, usageHighlight);

      PsiElement element = taintNode.getPsiElement();
      if (element == null) return;
      PsiElement elementHighlight = getIdentifier(element);
      if (elementHighlight == null) return;
      myMemberEditor.show(element, elementHighlight);
    }

    @Override
    public void dispose() {
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
      private final String myEmptyText;

      private ElementEditor(Editor editor, String emptyText) {
        myEditor = editor;
        myEmptyText = emptyText;
      }

      public void show(@Nullable PsiElement element, @Nullable PsiElement toHighlight) {
        if (element == null || toHighlight == null) {
          String text = JvmAnalysisBundle.message(myEmptyText);
          ApplicationManager.getApplication().runWriteAction(() -> myEditor.getDocument().setText(text));
          return;
        }
        ElementModel model = ElementModel.create(element, toHighlight);
        if (model == null) return;
        String text = model.myText;
        ApplicationManager.getApplication().runWriteAction(() -> myEditor.getDocument().setText(text));
        EditorHighlighter highlighter = createHighlighter(element);
        ((EditorEx)myEditor).setHighlighter(highlighter);
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

  private static class PropagateTree extends Tree implements DataProvider {
    private PropagateTree(TreeModel treeModel) {
      super(treeModel);
      getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    }

    @Override
    public @Nullable Object getData(@NotNull String dataId) {
      if (!CommonDataKeys.PSI_ELEMENT.is(dataId)) return null;
      TaintNode[] selectedNodes = getSelectedNodes(TaintNode.class, null);
      if (selectedNodes.length != 1) return null;
      return selectedNodes[0].getPsiElement();
    }

    @Contract("_, _ -> new")
    private static @NotNull PropagateTree create(@NotNull Disposable parent, @NotNull TaintNode root) {
      TaintNode rootWrapper = new TaintNode(null, null, null) {
        @Override
        public List<TaintNode> calcChildren() {
          return Collections.singletonList(root);
        }
      };
      BaseTreeModel<TaintNode> propagateTreeModel = new BaseTreeModel<>() {
        @Override
        public List<? extends TaintNode> getChildren(Object object) {
          TaintNode node = ObjectUtils.tryCast(object, TaintNode.class);
          if (node == null) return Collections.emptyList();
          return node.calcChildren();
        }

        @Override
        public TaintNode getRoot() {
          return rootWrapper;
        }
      };
      AsyncTreeModel treeModel = new AsyncTreeModel(propagateTreeModel, parent);
      PropagateTree tree = new PropagateTree(treeModel);
      tree.setRootVisible(false);
      tree.setShowsRootHandles(true);
      tree.setCellRenderer(new PropagateTree.PropagateTreeRenderer());
      TreeUtil.installActions(tree);
      EditSourceOnDoubleClickHandler.install(tree);
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
        PsiElement psiElement = taintNode.getPsiElement();
        if (psiElement == null) {
          append(UsageViewBundle.message("node.invalid"), SimpleTextAttributes.ERROR_ATTRIBUTES);
          return;
        }
        appendPsiElement(psiElement, taintNode);
        if (!taintNode.isTaintFlowRoot) return;
        String unsafeFlow = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.toolwindow.unsafe.flow");
        SimpleTextAttributes attributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, UIUtil.getLabelInfoForeground());
        append(unsafeFlow, attributes);
      }

      private void appendPsiElement(@NotNull PsiElement psiElement, @NotNull TaintNode taintNode) {
        int flags = Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS;
        setIcon(ReadAction.compute(() -> psiElement.getIcon(flags)));
        int style = taintNode.isExcluded() ? SimpleTextAttributes.STYLE_STRIKEOUT : SimpleTextAttributes.STYLE_PLAIN;
        Color color = taintNode.myTaintValue == TaintValue.TAINTED ? UIUtil.getErrorForeground() : null;
        SimpleTextAttributes attributes = new SimpleTextAttributes(style, color);
        PsiMethod psiMethod = ObjectUtils.tryCast(psiElement, PsiMethod.class);
        if (psiMethod != null) {
          PsiMethodRenderingInfo renderingInfo = new PsiMethodRenderingInfo(true);
          String text = renderingInfo.getPresentableText(psiMethod);
          append(text, attributes);
          return;
        }
        PsiVariable psiVariable = ObjectUtils.tryCast(psiElement, PsiVariable.class);
        if (psiVariable != null) {
          String varText =
            PsiFormatUtil.formatVariable(psiVariable, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE, PsiSubstitutor.EMPTY);
          append(varText, attributes);
          PsiNameIdentifierOwner parent = PsiTreeUtil.getParentOfType(psiVariable, PsiClass.class, PsiMethod.class);
          Color placeColor = attributes.getFgColor();
          if (placeColor == null) placeColor = UIUtil.getLabelInfoForeground();
          SimpleTextAttributes placeAttribute = new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, placeColor);
          if (parent instanceof PsiMethod) {
            PsiMethodRenderingInfo renderingInfo = new PsiMethodRenderingInfo(true);
            append(": " + renderingInfo.getPresentableText((PsiMethod)parent), placeAttribute);
          }
          else if (parent instanceof PsiClass) {
            PsiElementRenderingInfo<PsiClass> renderingInfo = PsiClassRenderingInfo.INSTANCE;
            append(": " + renderingInfo.getPresentableText((PsiClass)parent), placeAttribute);
          }
          return;
        }
        PsiNamedElement namedElement = ObjectUtils.tryCast(psiElement, PsiNamedElement.class);
        if (namedElement == null) return;
        String name = namedElement.getName();
        if (name == null) return;
        append(name, attributes);
      }
    }
  }
}
