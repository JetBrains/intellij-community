package com.intellij.debugger.ui;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.ui.impl.DebuggerTreeRenderer;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.SimpleColoredText;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.Value;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

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
            final Value value = evaluator.evaluate(evaluationContext);

            TextWithImports text = new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, myCurrentExpression.getText());
            final WatchItemDescriptor descriptor = new WatchItemDescriptor(myProject, text, value, false);
            descriptor.setContext(evaluationContext);
            if (myType == MOUSE_OVER_HINT) {
              // force using default renderer for mouse over hint in order to not to call accidentaly methods while rendering
              // otherwise, if the hint is invoked explicitly, show it with the right "auto" renderer
              descriptor.setRenderer(debuggerContext.getDebugProcess().getDefaultRenderer(value));
            }
            descriptor.updateRepresentation(evaluationContext, new DescriptorLabelListener() {
              public void labelChanged() {
                if(myCurrentRange != null) {
                  if(myType != MOUSE_OVER_HINT || descriptor.isValueValid()) {
                    showHint(DebuggerTreeRenderer.getDescriptorText(descriptor, true));
                  }
                }
              }
            });
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

  private void showHint(final SimpleColoredText text) {
    DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
      public void run() {
        if(myShowHint) {
          JComponent label = HintUtil.createInformationLabel(text);
          myCurrentHint = new LightweightHint(label);
          HintManager hintManager = HintManager.getInstance();

          //Editor may be disposed before later invokator process this action
          if(myEditor.getComponent() == null || myEditor.getComponent().getRootPane() == null) return;

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
  private boolean canProcess(PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression)expression).getQualifierExpression();
      return (qualifier == null)? true : canProcess(qualifier);
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

        if(psiFile == null) return;

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
}
