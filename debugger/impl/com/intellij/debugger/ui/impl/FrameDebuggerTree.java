/*
 * Class FrameDebuggerTree
 * @author Jeka
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.SuspendManager;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.settings.ViewsGeneralSettings;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.sun.jdi.ObjectCollectedException;

import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class FrameDebuggerTree extends DebuggerTree {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.FrameDebuggerTree");
  private boolean myAnyNewLocals;
  private boolean myAutoWatchMode = false;

  public FrameDebuggerTree(Project project) {
    super(project);
  }

  public boolean isAutoWatchMode() {
    return myAutoWatchMode;
  }

  public void setAutoVariablesMode(final boolean autoWatchMode) {
    final boolean valueChanged = myAutoWatchMode != autoWatchMode;
    myAutoWatchMode = autoWatchMode;
    if (valueChanged) {
      rebuild(getDebuggerContext());
    }
  }

  protected void build(DebuggerContextImpl context) {
    myAnyNewLocals = false;
    buildWhenPaused(context, new RefreshFrameTreeCommand(context));
  }

  public void restoreNodeState(DebuggerTreeNodeImpl node) {
    if (myAnyNewLocals) {
      final NodeDescriptorImpl descriptor = node.getDescriptor();
      final boolean isLocalVar = descriptor instanceof LocalVariableDescriptorImpl;
      descriptor.myIsSelected &= isLocalVar;
      // override this setting so that tree will scroll to new locals
      descriptor.myIsVisible = isLocalVar && descriptor.myIsSelected;
      if (!descriptor.myIsVisible) {
        descriptor.putUserData(VISIBLE_RECT, null);
      }
    }
    super.restoreNodeState(node);
    if (myAnyNewLocals && node.getDescriptor().myIsExpanded) {
      DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl)getMutableModel().getRoot();
      scrollToVisible(root);
    }
  }


  protected BuildNodeCommand getBuildNodeCommand(final DebuggerTreeNodeImpl node) {
    if (myAutoWatchMode && node.getDescriptor() instanceof StackFrameDescriptorImpl) {
      return new BuildAutoWatchStackFrameCommand(node);
    }
    return super.getBuildNodeCommand(node);
  }

  private class BuildAutoWatchStackFrameCommand extends BuildStackFrameCommand {
    public BuildAutoWatchStackFrameCommand(DebuggerTreeNodeImpl stackNode) {
      super(stackNode);
    }

    protected void buildVariables(final StackFrameDescriptorImpl stackDescriptor, final EvaluationContextImpl evaluationContext)
      throws EvaluateException {
      final SourcePosition sourcePosition = getDebuggerContext().getSourcePosition();
      if (sourcePosition == null) {
        return;
      }
      final Pair<Set<String>, Set<TextWithImports>> usedVars =
        ApplicationManager.getApplication().runReadAction(new Computable<Pair<Set<String>, Set<TextWithImports>>>() {
          public Pair<Set<String>, Set<TextWithImports>> compute() {
            return findReferencedVars(sourcePosition);
          }
        });
      if (usedVars.first.isEmpty() && usedVars.second.isEmpty()) {
        return;
      }
      final StackFrameProxyImpl frame = stackDescriptor.getFrameProxy();
      for (LocalVariableProxyImpl local : frame.visibleVariables()) {
        if (usedVars.first.contains(local.name())) {
          final LocalVariableDescriptorImpl descriptor = myNodeManager.getLocalVariableDescriptor(stackDescriptor, local);
          myChildren.add(myNodeManager.createNode(descriptor, evaluationContext));
        }
      }
      for (TextWithImports text : usedVars.second) {
        myChildren.add(myNodeManager.createNode(new WatchItemDescriptor(getProject(), text), evaluationContext));
      }
    }
  }

  private static boolean isLineEmpty(Document doc, int line) {
    int start = doc.getLineStartOffset(line);
    int end = doc.getLineEndOffset(line);
    return CharArrayUtil.shiftForward(doc.getCharsSequence(), start, " \n\t") >= end;
  }

  private static Pair<Set<String>, Set<TextWithImports>> findReferencedVars(final SourcePosition position) {
    final int line = position.getLine();
    if (line < 0) {
      return new Pair<Set<String>, Set<TextWithImports>>(Collections.<String>emptySet(), Collections.<TextWithImports>emptySet());
    }
    final PsiFile file = position.getFile();
    final Document doc = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
    if (doc == null || doc.getLineCount() == 0) {
      return new Pair<Set<String>, Set<TextWithImports>>(Collections.<String>emptySet(), Collections.<TextWithImports>emptySet());
    }

    int startLine = Math.max(0, line - 1);
    while (startLine > 0 && isLineEmpty(doc, startLine)) startLine--;
    final int startOffset = doc.getLineStartOffset(startLine);

    final int lastLine = doc.getLineCount() - 1;
    int endLine = Math.min(line + 2, lastLine);
    while (endLine < lastLine && isLineEmpty(doc, endLine)) endLine++;
    final int endOffset = doc.getLineEndOffset(endLine);

    final TextRange lineRange = new TextRange(startOffset, endOffset);
    if (!lineRange.isEmpty()) {
      final int offset = CharArrayUtil.shiftForward(doc.getCharsSequence(), doc.getLineStartOffset(line), " \t");
      PsiElement element = file.findElementAt(offset);
      if (element != null) {
        PsiMethod method = PsiTreeUtil.getNonStrictParentOfType(element, PsiMethod.class);
        if (method != null) {
          element = method;
        }
        else {
          PsiField field = PsiTreeUtil.getNonStrictParentOfType(element, PsiField.class);
          if (field != null) {
            element = field;
          }
          else {
            final PsiClassInitializer initializer = PsiTreeUtil.getNonStrictParentOfType(element, PsiClassInitializer.class);
            if (initializer != null) {
              element = initializer;
            }
          }
        }

        //noinspection unchecked
        final Set<String> vars = new HashSet<String>();
        final Set<TextWithImports> expressions = new HashSet<TextWithImports>();
        final PsiRecursiveElementVisitor variablesCollector = new VariablesCollector(lineRange, expressions, vars);
        element.accept(variablesCollector);

        return new Pair<Set<String>, Set<TextWithImports>>(vars, expressions);
      }
    }
    return new Pair<Set<String>, Set<TextWithImports>>(Collections.<String>emptySet(), Collections.<TextWithImports>emptySet());
  }

  private static boolean hasSideEffects(PsiElement element) {
    final AtomicBoolean rv = new AtomicBoolean(false);
    element.accept(new PsiRecursiveElementVisitor() {
      public void visitPostfixExpression(final PsiPostfixExpression expression) {
        rv.set(true);
      }

      public void visitPrefixExpression(final PsiPrefixExpression expression) {
        final IElementType op = expression.getOperationTokenType();
        if (JavaTokenType.PLUSPLUS.equals(op) || JavaTokenType.MINUSMINUS.equals(op)) {
          rv.set(true);
        }
        else {
          super.visitPrefixExpression(expression);
        }
      }

      public void visitAssignmentExpression(final PsiAssignmentExpression expression) {
        rv.set(true);
      }

      public void visitCallExpression(final PsiCallExpression callExpression) {
        rv.set(true);
      }
    });
    return rv.get();
  }


  private class RefreshFrameTreeCommand extends RefreshDebuggerTreeCommand {
    public RefreshFrameTreeCommand(DebuggerContextImpl context) {
      super(context);
    }

    public void threadAction() {
      super.threadAction();
      DebuggerTreeNodeImpl rootNode;

      final ThreadReferenceProxyImpl currentThread = getDebuggerContext().getThreadProxy();
      if (currentThread == null) {
        return;
      }

      try {
        StackFrameProxyImpl frame = getDebuggerContext().getFrameProxy();

        if (frame != null) {
          NodeManagerImpl nodeManager = getNodeFactory();
          rootNode = nodeManager.createNode(nodeManager.getStackFrameDescriptor(null, frame), getDebuggerContext().createEvaluationContext());
        }
        else {
          rootNode = getNodeFactory().getDefaultNode();
          SuspendManager suspendManager = getSuspendContext().getDebugProcess().getSuspendManager();
          try {
            if (suspendManager.isSuspended(currentThread)) {
              try {
                if (currentThread.frameCount() == 0) {
                  rootNode.add(MessageDescriptor.THREAD_IS_EMPTY);
                }
                else {
                  rootNode.add(MessageDescriptor.DEBUG_INFO_UNAVAILABLE);
                }
              }
              catch (EvaluateException e) {
                rootNode.add(new MessageDescriptor(e.getMessage()));
              }
            }
            else {
              rootNode.add(MessageDescriptor.THREAD_IS_RUNNING);
            }
          }
          catch (ObjectCollectedException e) {
            rootNode.add(new MessageDescriptor(DebuggerBundle.message("label.thread.node.thread.collected", currentThread.name())));
          }
        }
      }
      catch (Exception ex) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(ex);
        }
        rootNode = getNodeFactory().getDefaultNode();
        rootNode.add(MessageDescriptor.DEBUG_INFO_UNAVAILABLE);
      }

      final DebuggerTreeNodeImpl rootNode1 = rootNode;
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        public void run() {
          getMutableModel().setRoot(rootNode1);
          treeChanged();

          final TreeModel model = getModel();
          model.addTreeModelListener(new TreeModelAdapter() {
            public void treeStructureChanged(TreeModelEvent e) {
              final Object[] path = e.getPath();
              if (path.length > 0 && path[path.length - 1] == rootNode1) {
                // wait until rootNode1 (the root just set) becomes the root
                model.removeTreeModelListener(this);
                if (ViewsGeneralSettings.getInstance().AUTOSCROLL_TO_NEW_LOCALS) {
                  autoscrollToNewLocals(rootNode1);
                }
                else {
                  // should clear this flag, otherwise, if AUTOSCROLL_TO_NEW_LOCALS option turned
                  // to true during the debug process, all these variables will be considered 'new'
                  for (Enumeration children = rootNode1.rawChildren(); children.hasMoreElements();) {
                    final DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl)children.nextElement();
                    final NodeDescriptorImpl descriptor = child.getDescriptor();
                    if (descriptor instanceof LocalVariableDescriptorImpl) {
                      ((LocalVariableDescriptorImpl)descriptor).setNewLocal(false);
                    }
                  }
                }
              }
            }
          });
        }

        private void autoscrollToNewLocals(DebuggerTreeNodeImpl frameNode) {
          final DebuggerSession debuggerSession = getDebuggerContext().getDebuggerSession();
          final boolean isSteppingThrough = debuggerSession.isSteppingThrough(getDebuggerContext().getThreadProxy());
          for (Enumeration e = frameNode.rawChildren(); e.hasMoreElements();) {
            final DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl)e.nextElement();
            final NodeDescriptorImpl descriptor = child.getDescriptor();
            if (!(descriptor instanceof LocalVariableDescriptorImpl)) {
              continue;
            }
            final LocalVariableDescriptorImpl localVariableDescriptor = (LocalVariableDescriptorImpl)descriptor;
            if (isSteppingThrough && localVariableDescriptor.isNewLocal()) {
              TreePath treePath = new TreePath(child.getPath());
              addSelectionPath(treePath);
              myAnyNewLocals = true;
              descriptor.myIsSelected = true;
            }
            else {
              removeSelectionPath(new TreePath(child.getPath()));
              descriptor.myIsSelected = false;
            }
            localVariableDescriptor.setNewLocal(false);
          }
        }
      });
    }
  }

  private static class VariablesCollector extends PsiRecursiveElementVisitor {
    private final TextRange myLineRange;
    private final Set<TextWithImports> myExpressions;
    private final Set<String> myVars;

    public VariablesCollector(final TextRange lineRange, final Set<TextWithImports> expressions, final Set<String> vars) {
      myLineRange = lineRange;
      myExpressions = expressions;
      myVars = vars;
    }

    public void visitElement(final PsiElement element) {
      if (myLineRange.intersects(element.getTextRange())) {
        super.visitElement(element);
      }
    }

    public void visitReferenceExpression(final PsiReferenceExpression reference) {
      if (myLineRange.intersects(reference.getTextRange())) {
        final PsiElement psiElement = reference.resolve();
        if (psiElement instanceof PsiVariable) {
          final PsiVariable var = (PsiVariable)psiElement;
          if (var instanceof PsiField && !hasSideEffects(reference)) {
            myExpressions.add(new TextWithImportsImpl(reference));
          }
          else {
            myVars.add(var.getName());
          }
        }
      }
      super.visitReferenceExpression(reference);
    }

    public void visitArrayAccessExpression(final PsiArrayAccessExpression expression) {
      if (!hasSideEffects(expression)) {
        myExpressions.add(new TextWithImportsImpl(expression));
      }
      super.visitArrayAccessExpression(expression);
    }

    public void visitLocalVariable(final PsiLocalVariable variable) {
      if (myLineRange.intersects(variable.getTextRange())) {
        myVars.add(variable.getName());
      }
      super.visitLocalVariable(variable);
    }

    public void visitClass(final PsiClass aClass) {
      // Do not step in to local and anonymous classes...
    }
  }
}