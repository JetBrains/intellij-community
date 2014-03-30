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
package com.intellij.refactoring.changeSignature;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Set;

public abstract class CallerChooserBase<M extends PsiElement> extends DialogWrapper {
  private final M myMethod;
  private final Alarm myAlarm = new Alarm();
  private MethodNodeBase<M> myRoot;
  protected final Project myProject;
  private Tree myTree;
  private final Consumer<Set<M>> myCallback;
  private TreeSelectionListener myTreeSelectionListener;
  private Editor myCallerEditor;
  private Editor myCalleeEditor;
  private final boolean myInitDone;
  private final String myFileName;

  protected abstract MethodNodeBase<M> createTreeNode(M method, HashSet<M> called, Runnable cancelCallback);

  protected abstract M[] findDeepestSuperMethods(M method);

  public CallerChooserBase(M method, Project project, String title, Tree previousTree, String fileName, Consumer<Set<M>> callback) {
    super(true);
    myMethod = method;
    myProject = project;
    myTree = previousTree;
    myFileName = fileName;
    myCallback = callback;
    setTitle(title);
    init();
    myInitDone = true;
  }

  public Tree getTree() {
    return myTree;
  }

  @Override
  protected JComponent createCenterPanel() {
    Splitter splitter = new Splitter(false, (float)0.6);
    JPanel result = new JPanel(new BorderLayout());
    if (myTree == null) {
      myTree = createTree();
    }
    else {
      final CheckedTreeNode root = (CheckedTreeNode)myTree.getModel().getRoot();
      myRoot = (MethodNodeBase)root.getFirstChild();
    }
    myTreeSelectionListener = new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath path = e.getPath();
        if (path != null) {
          final MethodNodeBase<M> node = (MethodNodeBase)path.getLastPathComponent();
          myAlarm.cancelAllRequests();
          myAlarm.addRequest(new Runnable() {
            @Override
            public void run() {
              updateEditorTexts(node);
            }
          }, 300);
        }
      }
    };
    myTree.getSelectionModel().addTreeSelectionListener(myTreeSelectionListener);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    splitter.setFirstComponent(scrollPane);
    final JComponent callSitesViewer = createCallSitesViewer();
    TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath == null) {
      selectionPath = new TreePath(myRoot.getPath());
      myTree.getSelectionModel().addSelectionPath(selectionPath);
    }

    final MethodNodeBase<M> node = (MethodNodeBase)selectionPath.getLastPathComponent();
    updateEditorTexts(node);

    splitter.setSecondComponent(callSitesViewer);
    result.add(splitter);
    return result;
  }

  private void updateEditorTexts(final MethodNodeBase<M> node) {
    final MethodNodeBase<M> parentNode = (MethodNodeBase)node.getParent();
    final String callerText = node != myRoot ? getText(node.getMethod()) : "";
    final Document callerDocument = myCallerEditor.getDocument();
    final String calleeText = node != myRoot ? getText(parentNode.getMethod()) : "";
    final Document calleeDocument = myCalleeEditor.getDocument();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        callerDocument.setText(callerText);
        calleeDocument.setText(calleeText);
      }
    });

    final M caller = node.getMethod();
    final PsiElement callee = parentNode != null ? parentNode.getElementToSearch() : null;
    if (caller != null && caller.isPhysical() && callee != null) {
      HighlightManager highlighter = HighlightManager.getInstance(myProject);
      EditorColorsManager colorManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES);
      int start = getStartOffset(caller);
      for (PsiElement element : findElementsToHighlight(caller, callee)) {
        highlighter.addRangeHighlight(myCallerEditor, element.getTextRange().getStartOffset() - start,
                                      element.getTextRange().getEndOffset() - start, attributes, false, null);
      }
    }
  }

  protected Collection<PsiElement> findElementsToHighlight(M caller, PsiElement callee) {
    Query<PsiReference> references = ReferencesSearch.search(callee, new LocalSearchScope(caller), false);
    return ContainerUtil.mapNotNull(references, new Function<PsiReference, PsiElement>() {
      @Override
      public PsiElement fun(PsiReference psiReference) {
        return psiReference.getElement();
      }
    });
  }

  @Override
  public void dispose() {
    if (myTree != null) {
      myTree.removeTreeSelectionListener(myTreeSelectionListener);
      EditorFactory.getInstance().releaseEditor(myCallerEditor);
      EditorFactory.getInstance().releaseEditor(myCalleeEditor);
    }
    super.dispose();
  }

  private String getText(final M method) {
    if (method == null) return "";
    final PsiFile file = method.getContainingFile();
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
    if (document != null) {
      final int start = document.getLineStartOffset(document.getLineNumber(method.getTextRange().getStartOffset()));
      final int end = document.getLineEndOffset(document.getLineNumber(method.getTextRange().getEndOffset()));
      return document.getText().substring(start, end);
    }
    return "";
  }

  private int getStartOffset(@NotNull final M method) {
    final PsiFile file = method.getContainingFile();
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
    return document.getLineStartOffset(document.getLineNumber(method.getTextRange().getStartOffset()));
  }

  private JComponent createCallSitesViewer() {
    Splitter splitter = new Splitter(true);
    myCallerEditor = createEditor();
    myCalleeEditor = createEditor();
    final JComponent callerComponent = myCallerEditor.getComponent();
    callerComponent.setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("caller.chooser.caller.method"),
                                                                  false));
    splitter.setFirstComponent(callerComponent);
    final JComponent calleeComponent = myCalleeEditor.getComponent();
    calleeComponent.setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("caller.chooser.callee.method"),
                                                                  false));
    splitter.setSecondComponent(calleeComponent);
    splitter.setBorder(IdeBorderFactory.createRoundedBorder());
    return splitter;
  }

  private Editor createEditor() {
    final EditorFactory editorFactory = EditorFactory.getInstance();
    final Document document = editorFactory.createDocument("");
    final Editor editor = editorFactory.createViewer(document, myProject);
    ((EditorEx)editor).setHighlighter(HighlighterFactory.createHighlighter(myProject, myFileName));
    return editor;
  }

  private Tree createTree() {
    final Runnable cancelCallback = new Runnable() {
      @Override
      public void run() {
        if (myInitDone) {
          close(CANCEL_EXIT_CODE);
        }
        else {
          throw new ProcessCanceledException();
        }
      }
    };
    final CheckedTreeNode root = createTreeNode(null, new HashSet<M>(), cancelCallback);
    myRoot = createTreeNode(myMethod, new HashSet<M>(), cancelCallback);
    root.add(myRoot);
    final CheckboxTree.CheckboxTreeCellRenderer cellRenderer = new CheckboxTree.CheckboxTreeCellRenderer(true, false) {
      @Override
      public void customizeRenderer(JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
        if (value instanceof MethodNodeBase) {
          ((MethodNodeBase)value).customizeRenderer(getTextRenderer());
        }
      }
    };
    Tree tree = new CheckboxTree(cellRenderer, root, new CheckboxTreeBase.CheckPolicy(false, true, true, false));
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.getSelectionModel().setSelectionPath(new TreePath(myRoot.getPath()));

    return tree;
  }

  private void getSelectedMethods(Set<M> methods) {
    MethodNodeBase<M> node = myRoot;
    getSelectedMethodsInner(node, methods);
    methods.remove(node.getMethod());
  }

  private void getSelectedMethodsInner(final MethodNodeBase<M> node, final Set<M> allMethods) {
    if (node.isChecked()) {
      M method = node.getMethod();
      final M[] superMethods = findDeepestSuperMethods(method);
      if (superMethods.length == 0) {
        allMethods.add(method);
      }
      else {
        allMethods.addAll(Arrays.asList(superMethods));
      }

      final Enumeration children = node.children();
      while (children.hasMoreElements()) {
        getSelectedMethodsInner((MethodNodeBase)children.nextElement(), allMethods);
      }
    }
  }

  @Override
  protected void doOKAction() {
    final Set<M> selectedMethods = new HashSet<M>();
    getSelectedMethods(selectedMethods);
    myCallback.consume(selectedMethods);
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "caller.chooser.dialog";
  }
}
