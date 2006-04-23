package com.intellij.debugger.ui;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.impl.DebuggerTreeRenderer;
import com.intellij.debugger.ui.impl.InspectDebuggerTree;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.Value;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;

/**
 * User: lex
 * Date: Nov 24, 2003
 * Time: 7:31:26 PM
 */
public class ValueHint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.ValueHint");
  public final static int MOUSE_OVER_HINT       = 0;
  public final static int MOUSE_ALT_OVER_HINT   = 1;
  public final static int MOUSE_CLICK_HINT      = 2;

  private static final int HINT_TIMEOUT = 7000; // ms

  private static TextAttributes ourReferenceAttributes;
  static {
    ourReferenceAttributes = new TextAttributes();
    ourReferenceAttributes.setForegroundColor(Color.blue);
    ourReferenceAttributes.setEffectColor(Color.blue);
    ourReferenceAttributes.setEffectType(EffectType.LINE_UNDERSCORE);
  }

  private final Project myProject;
  private final Editor myEditor;
  private final Point myPoint;

  private LightweightHint myCurrentHint = null;
  private JBPopup myPopup = null;
  private TextRange myCurrentRange = null;
  private RangeHighlighter myHighlighter = null;
  private Cursor myStoredCursor = null;
  private PsiExpression myCurrentExpression = null;

  private final int myType;

  private boolean myShowHint = true;

  private KeyListener myEditorKeyListener = new KeyListener() {
    public void keyTyped(KeyEvent e) {
    }

    public void keyPressed(KeyEvent e) {
    }

    public void keyReleased(KeyEvent e) {
      if(!ValueLookupManager.isAltMask(e.getModifiers())) {
        ValueLookupManager.getInstance(myProject).hideHint();
      }
    }
  };

  public ValueHint(Project project, Editor editor, Point point, int type) {
    myProject = project;
    myEditor = editor;
    myPoint = point;
    myType = type;
    myCurrentExpression = getSelectedExpression();
  }

  public void hideHint() {
    myShowHint = false;
    myCurrentRange = null;
    if(myStoredCursor != null) {
      Component internalComponent = myEditor.getContentComponent();
      internalComponent.setCursor(myStoredCursor);
      if (LOG.isDebugEnabled()) {
        LOG.debug("internalComponent.setCursor(myStoredCursor)");
      }
      internalComponent.removeKeyListener(myEditorKeyListener);
    }

    if(myCurrentHint != null) {
      myCurrentHint.hide();
      myCurrentHint = null;
    }
    if(myHighlighter != null) {
      myEditor.getMarkupModel().removeHighlighter(myHighlighter);
      myHighlighter = null;
    }
  }

  public void invokeHint() {
    if(myCurrentExpression == null) {
      hideHint();
      return;
    }

    if(myType == MOUSE_ALT_OVER_HINT){
      myHighlighter = myEditor.getMarkupModel().addRangeHighlighter(myCurrentRange.getStartOffset(), myCurrentRange.getEndOffset(), HighlighterLayer.SELECTION + 1, ourReferenceAttributes, HighlighterTargetArea.EXACT_RANGE);
      Component internalComponent = myEditor.getContentComponent();
      myStoredCursor = internalComponent.getCursor();
      internalComponent.addKeyListener(myEditorKeyListener);
      internalComponent.setCursor(hintCursor());
      if (LOG.isDebugEnabled()) {
        LOG.debug("internalComponent.setCursor(hintCursor())");
      }
    } else {
      evaluateHint();
    }
  }

  private void evaluateHint() {
    final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(myProject).getContext();

    final DebuggerSession debuggerSession = debuggerContext.getDebuggerSession();
    if(debuggerSession == null || !debuggerSession.isPaused()) return;

    try {
      final ExpressionEvaluator evaluator = EvaluatorBuilderImpl.getInstance().build(myCurrentExpression);

      debuggerContext.getDebugProcess().getManagerThread().invokeLater(new DebuggerContextCommandImpl(debuggerContext) {
        public void threadAction() {
          try {
            final EvaluationContextImpl evaluationContext = debuggerContext.createEvaluationContext();


            final TextWithImports text = new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, myCurrentExpression.getText());
            final Value value = evaluator.evaluate(evaluationContext);

            final WatchItemDescriptor descriptor = new WatchItemDescriptor(myProject, text, value, false);
            if (value instanceof PrimitiveValue || myType == MOUSE_OVER_HINT) {
              descriptor.setContext(evaluationContext);
              if (myType == MOUSE_OVER_HINT) {
                // force using default renderer for mouse over hint in order to not to call accidentaly methods while rendering
                // otherwise, if the hint is invoked explicitly, show it with the right "auto" renderer
                descriptor.setRenderer(debuggerContext.getDebugProcess().getDefaultRenderer(value));
              }
              descriptor.updateRepresentation(evaluationContext, new DescriptorLabelListener() {
                public void labelChanged() {
                  if(myCurrentRange != null) {
                    if( myType != MOUSE_OVER_HINT || descriptor.isValueValid()) {
                      final SimpleColoredText simpleColoredText = DebuggerTreeRenderer.getDescriptorText(descriptor, true);
                      if (!(value instanceof PrimitiveValue)){
                        simpleColoredText.append(" (" + DebuggerBundle.message("active.tooltip.suggestion") + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                      }
                      showHint(simpleColoredText);
                    }
                  }
                }
              });
            } else {
              final InspectDebuggerTree tree = new InspectDebuggerTree(myProject);
              tree.getModel().addTreeModelListener(new TreeModelListener() {
                public void treeNodesChanged(TreeModelEvent e) {
                  //do nothing
                }

                public void treeNodesInserted(TreeModelEvent e) {
                  //do nothing
                }

                public void treeNodesRemoved(TreeModelEvent e) {
                  //do nothing
                }

                public void treeStructureChanged(TreeModelEvent e) {
                  resize(e.getTreePath(), tree);
                }
              });
              tree.setInspectDescriptor(descriptor);
              showHint(tree, debuggerContext, myCurrentExpression.getText(), new ActiveTooltipComponent(tree, myCurrentExpression.getText()));
            }
          }
          catch (EvaluateException e) {
            LOG.debug(e);
          }
        }

      }, DebuggerManagerThreadImpl.HIGH_PRIORITY);
    }
    catch (EvaluateException e) {
      LOG.debug(e);
    }
  }

  private void resize(final TreePath path, DebuggerTree tree) {
    if (myPopup == null) return;
    final Window popupWindow = SwingUtilities.windowForComponent(myPopup.getContent());
    final Dimension size = tree.getPreferredSize();
    final Point location = popupWindow.getLocation();
    final Rectangle windowBounds = popupWindow.getBounds();
    final Rectangle bounds = tree.getPathBounds(path);
    final Rectangle targetBounds = new Rectangle(location.x,
                                                 location.y,
                                                 Math.max(Math.max(size.width, bounds.width) + 20, windowBounds.width),
                                                 Math.max(tree.getRowCount() * bounds.height + 55, windowBounds.height));
    ScreenUtil.cropRectangleToFitTheScreen(targetBounds);
    popupWindow.setBounds(targetBounds);
    popupWindow.validate();
    popupWindow.repaint();
  }

  private void showHint(final InspectDebuggerTree tree,
                        final DebuggerContextImpl debuggerContext,
                        final String title,
                        final ActiveTooltipComponent component) {
    DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
      public void run() {
        tree.rebuild(debuggerContext);
        if (myPopup != null) {
          myPopup.cancel();
        }
        myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(component, tree)
          .setRequestFocus(true)
          .setTitle(title)
          .setResizable(true)
          .setMovable(true)
          .createPopup();

        //Editor may be disposed before later invokator process this action
        if (myEditor.getComponent().getRootPane() == null) return;
        myPopup.show(new RelativePoint(myEditor.getContentComponent(), myPoint));

        updateInitialBounds(tree);
      }
    });
  }



  private void updateInitialBounds(final InspectDebuggerTree tree) {
    final Window popupWindow = SwingUtilities.windowForComponent(myPopup.getContent());
    final Dimension size = tree.getPreferredSize();
    final Point location = popupWindow.getLocation();
    final Rectangle windowBounds = popupWindow.getBounds();
    final Rectangle targetBounds = new Rectangle(location.x,
                                                 location.y,
                                                 Math.max(size.width + 250, windowBounds.width),
                                                 Math.max(size.height, windowBounds.height));
    ScreenUtil.cropRectangleToFitTheScreen(targetBounds);
    popupWindow.setBounds(targetBounds);
    popupWindow.validate();
    popupWindow.repaint();
  }

  private void showHint(final SimpleColoredText text) {
    DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
      public void run() {
        if(myShowHint) {
          JComponent label = HintUtil.createInformationLabel(text);
          myCurrentHint = new LightweightHint(label);
          HintManager hintManager = HintManager.getInstance();

          //Editor may be disposed before later invokator process this action
          if(myEditor.getComponent().getRootPane() == null) return;

          Point p = hintManager.getHintPosition(myCurrentHint, myEditor, myEditor.xyToLogicalPosition(myPoint), HintManager.UNDER);
          hintManager.showEditorHint(
            myCurrentHint,
            myEditor,
            p,
            HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING,
            HINT_TIMEOUT,
            false);
          if(myType == MOUSE_CLICK_HINT) {
            label.requestFocusInWindow();
          }
        }
      }
    });
  }

  // call inside ReadAction only
  private static boolean canProcess(PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression)expression).getQualifierExpression();
      return qualifier == null || canProcess(qualifier);
    }
    return !(expression instanceof PsiMethodCallExpression);
  }

  private PsiExpression findExpression(PsiElement element) {
    if (!(element instanceof PsiIdentifier || element instanceof PsiKeyword)) {
      return null;
    }

    PsiElement expression = null;
    PsiElement parent = element.getParent();
    if (parent instanceof PsiVariable) {
      expression = element;
    }
    else if (parent instanceof PsiReferenceExpression && canProcess((PsiExpression)parent)) {
      expression = parent;
    }
    if (parent instanceof PsiThisExpression) {
      expression = parent;
    }
    try {
      if (expression != null) {
        PsiElement context = element;
        if(parent instanceof PsiParameter) {
          try {
            context = ((PsiMethod)((PsiParameter)parent).getDeclarationScope()).getBody();
          }
          catch (Throwable e) {}
        } else {
          while(context != null  && !(context instanceof PsiStatement) && !(context instanceof PsiClass)) {
            context = context.getParent();
          }
        }
        myCurrentRange = expression.getTextRange();
        return expression.getManager().getElementFactory().createExpressionFromText(expression.getText(), context);
      }
    } catch (IncorrectOperationException e) {
      LOG.debug(e);
    }
    return null;
  }

  private PsiExpression getSelectedExpression() {
    final PsiExpression[] selectedExpression = new PsiExpression[] { null };

    PsiDocumentManager.getInstance(myProject).commitAndRunReadAction(new Runnable() {
      public void run() {
        // Point -> offset
        final int offset = calcOffset(myEditor, myPoint);


        PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());

        if(psiFile == null || !psiFile.isValid()) return;

        int selectionStart = myEditor.getSelectionModel().getSelectionStart();
        int selectionEnd   = myEditor.getSelectionModel().getSelectionEnd();

        if(isRequestSelection() && (selectionStart <= offset && offset <= selectionEnd)) {
          PsiElement ctx = (selectionStart > 0) ? psiFile.findElementAt(selectionStart - 1) : psiFile.findElementAt(selectionStart);
          try {
            String text = myEditor.getSelectionModel().getSelectedText();
            if(text != null && ctx != null) {
              selectedExpression[0] = PsiManager.getInstance(myProject).getElementFactory().createExpressionFromText(text, ctx);
              myCurrentRange = new TextRange(myEditor.getSelectionModel().getSelectionStart(), myEditor.getSelectionModel().getSelectionEnd());
            }
          } catch (IncorrectOperationException e) {
          }
        }

        if(myCurrentRange == null) {
          PsiElement elementAtCursor = psiFile.findElementAt(offset);
          if (elementAtCursor == null) return;
          PsiExpression expression;
          expression = findExpression(elementAtCursor);
          if (expression == null) return;
          selectedExpression[0] = expression;
        }
      }
    });
    return selectedExpression[0];
  }

  private boolean isRequestSelection() {
    return (myType == MOUSE_CLICK_HINT || myType == MOUSE_ALT_OVER_HINT);
  }

  public boolean isKeepHint(Editor editor, Point point) {
    if(myType == ValueHint.MOUSE_ALT_OVER_HINT) {
      return false;
    }
    else if(myType == ValueHint.MOUSE_CLICK_HINT) {
      if(myCurrentHint != null && myCurrentHint.isVisible()) {
        return true;
      }
    }
    else {
      int offset = calcOffset(editor, point);

      if (myCurrentRange != null && myCurrentRange.getStartOffset() <= offset && offset <= myCurrentRange.getEndOffset()) {
        return true;
      }
    }
    return false;
  }

  private static int calcOffset(Editor editor, Point point) {
    LogicalPosition pos = editor.xyToLogicalPosition(point);
    return editor.logicalPositionToOffset(pos);
  }

  private static Cursor hintCursor() {
    return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  }

  private class ActiveTooltipComponent extends JPanel {
    private static final int HISTORY_SIZE = 11;
    private ArrayList<Pair<NodeDescriptorImpl, String>> myHistory = new ArrayList<Pair<NodeDescriptorImpl, String>>();
    private InspectDebuggerTree myTree;
    private int myCurrentIndex = -1;
    public ActiveTooltipComponent(InspectDebuggerTree tree, final String title) {
      super(new BorderLayout());
      myTree = tree;
      myHistory.add(Pair.create(myTree.getInspectDescriptor(), title));
      add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);
      add(createToolbar(), BorderLayout.NORTH);
    }

    private JComponent createToolbar() {
      DefaultActionGroup group = new DefaultActionGroup();
      group.add(createSetRoot());

      AnAction back = createGoBackAction();
      back.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.ALT_MASK)), this);
      group.add(back);

      AnAction forward = createGoForwardAction();
      forward.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.ALT_MASK)), this);
      group.add(forward);

      return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();
    }

    private AnAction createGoForwardAction(){
      return new AnAction(CodeInsightBundle.message("quick.definition.forward"), null, IconLoader.getIcon("/actions/forward.png")){
        public void actionPerformed(AnActionEvent e) {
          if (myHistory.size() > 1 && myCurrentIndex < myHistory.size() - 1){
            myCurrentIndex ++;
            updateHintAccordingToHistory(myHistory.get(myCurrentIndex));
          }
        }


        public void update(AnActionEvent e) {
          e.getPresentation().setEnabled(myHistory.size() > 1 && myCurrentIndex < myHistory.size() - 1);
        }
      };
    }


    private AnAction createGoBackAction(){
      return new AnAction(CodeInsightBundle.message("quick.definition.back"), null, IconLoader.getIcon("/actions/back.png")){
        public void actionPerformed(AnActionEvent e) {
          if (myHistory.size() > 1 && myCurrentIndex > 0) {
            myCurrentIndex--;
            updateHintAccordingToHistory(myHistory.get(myCurrentIndex));
          }
        }


        public void update(AnActionEvent e) {
          e.getPresentation().setEnabled(myHistory.size() > 1 && myCurrentIndex > 0);
        }
      };
    }

    private void updateHintAccordingToHistory(Pair<NodeDescriptorImpl, String> descriptorWithTitle){
      final NodeDescriptorImpl descriptor = descriptorWithTitle.first;
      final String title = descriptorWithTitle.second;
      final DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(myProject)).getContext();
      context.getDebugProcess().getManagerThread().invokeLater(new DebuggerContextCommandImpl(context) {
        public void threadAction() {
          myTree.setInspectDescriptor(descriptor);
          showHint(myTree, context, title, ActiveTooltipComponent.this);
        }
      });
    }


    private AnAction createSetRoot() {
      final String title = DebuggerBundle.message("active.tooltip.set.root.title");
      return new AnAction(title, title, IconLoader.getIcon("/modules/unmarkWebroot.png")) {
        public void actionPerformed(AnActionEvent e) {
          final TreePath path = myTree.getSelectionPath();
          if (path == null) return;
          final Object node = path.getLastPathComponent();
          if (node instanceof DebuggerTreeNodeImpl) {
            final DebuggerTreeNodeImpl debuggerTreeNode = (DebuggerTreeNodeImpl)node;
            final DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(myProject)).getContext();
            context.getDebugProcess().getManagerThread().invokeLater(new DebuggerContextCommandImpl(context) {
              public void threadAction() {
                try {
                  final NodeDescriptorImpl descriptor = debuggerTreeNode.getDescriptor();
                  final TextWithImports evaluationText = DebuggerTreeNodeExpression.createEvaluationText(debuggerTreeNode, context);
                  final String title = evaluationText.getText();
                  if (myCurrentIndex < HISTORY_SIZE) {
                    if (myCurrentIndex != -1) {
                      myCurrentIndex += 1;
                    } else {
                      myCurrentIndex = 1;
                    }
                    myHistory.add(myCurrentIndex, Pair.create(descriptor, title));
                  }
                  myTree.setInspectDescriptor(descriptor);
                  showHint(myTree, context, title, ActiveTooltipComponent.this);
                }
                catch (final EvaluateException e1) {
                  LOG.debug(e1);
                }
              }
            });
          }
        }
      };
    }
  }
}
