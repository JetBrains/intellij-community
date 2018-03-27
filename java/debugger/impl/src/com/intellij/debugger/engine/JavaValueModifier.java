// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.EvaluatingComputable;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.*;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.frame.XValueModifier;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

/*
 * Class SetValueAction
 * @author Jeka
 */
public abstract class JavaValueModifier extends XValueModifier {
  private final JavaValue myJavaValue;

  public JavaValueModifier(JavaValue javaValue) {
    myJavaValue = javaValue;
  }

  @Override
  public void calculateInitialValueEditorText(final XInitialValueCallback callback) {
    final Value value = myJavaValue.getDescriptor().getValue();
    if (value == null || value instanceof PrimitiveValue) {
      String valueString = myJavaValue.getValueString();
      int pos = valueString.lastIndexOf('('); //skip hex presentation if any
      if (pos > 1) {
        valueString = valueString.substring(0, pos).trim();
      }
      callback.setValue(valueString);
    }
    else if (value instanceof StringReference) {
      final EvaluationContextImpl evaluationContext = myJavaValue.getEvaluationContext();
      evaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(evaluationContext.getSuspendContext()) {
        @Override
        public Priority getPriority() {
          return Priority.NORMAL;
        }

        @Override
        public void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
          callback.setValue(
            StringUtil.wrapWithDoubleQuote(DebuggerUtils.translateStringValue(DebuggerUtils.getValueAsString(evaluationContext, value))));
        }
      });
    }
    else {
      callback.setValue(null);
    }
  }

  //public void update(AnActionEvent e) {
  //  boolean enable = false;
  //  DebuggerTreeNodeImpl node = getSelectedNode(e.getDataContext());
  //  if (node != null) {
  //    NodeDescriptorImpl descriptor = node.getDescriptor();
  //    if(descriptor instanceof ValueDescriptorImpl){
  //      ValueDescriptorImpl valueDescriptor = ((ValueDescriptorImpl)descriptor);
  //      enable = valueDescriptor.canSetValue();
  //    }
  //  }
  //  e.getPresentation().setVisible(enable);
  //}
  //
  protected static void update(final DebuggerContextImpl context) {
    DebuggerInvocationUtil.swingInvokeLater(context.getProject(), () -> {
      final DebuggerSession session = context.getDebuggerSession();
      if (session != null) {
        session.refresh(false);
      }
    });
    //node.setState(context);
  }

  protected abstract void setValueImpl(@NotNull String expression, @NotNull XModificationCallback callback);

  @Override
  public void setValue(@NotNull String expression, @NotNull XModificationCallback callback) {
    final NodeDescriptorImpl descriptor = myJavaValue.getDescriptor();
    if(!((ValueDescriptorImpl)descriptor).canSetValue()) {
      return;
    }

    if (myJavaValue.getEvaluationContext().getSuspendContext().isResumed()) {
      callback.errorOccurred(DebuggerBundle.message("error.context.has.changed"));
      return;
    }

    setValueImpl(expression, callback);
  }

  protected static Value preprocessValue(EvaluationContextImpl context, Value value, Type varType) throws EvaluateException {
    if (value != null && JAVA_LANG_STRING.equals(varType.name()) && !(value instanceof StringReference)) {
      String v = DebuggerUtils.getValueAsString(context, value);
      if (v != null) {
        value = context.getSuspendContext().getDebugProcess().getVirtualMachineProxy().mirrorOf(v);
      }
    }
    if (value instanceof DoubleValue) {
      double dValue = ((DoubleValue) value).doubleValue();
      if(varType instanceof FloatType && Float.MIN_VALUE <= dValue && dValue <= Float.MAX_VALUE){
        value = context.getSuspendContext().getDebugProcess().getVirtualMachineProxy().mirrorOf((float)dValue);
      }
    }
    if (value != null) {
      if (varType instanceof PrimitiveType) {
        if (!(value instanceof PrimitiveValue)) {
          value = (Value)new UnBoxingEvaluator(new IdentityEvaluator(value)).evaluate(context);
        }
      }
      else if (varType instanceof ReferenceType) {
        if (value instanceof PrimitiveValue) {
          value = (Value)new BoxingEvaluator(new IdentityEvaluator(value)).evaluate(context);
        }
      }
    }
    return value;
  }

  protected interface SetValueRunnable {
    void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException,
                                                                                          InvalidTypeException,
                                                                                          EvaluateException,
                                                                                          IncompatibleThreadStateException;
    ReferenceType loadClass(EvaluationContextImpl evaluationContext, String className) throws EvaluateException,
                                                                                            InvocationException,
                                                                                            ClassNotLoadedException,
                                                                                            IncompatibleThreadStateException,
                                                                                            InvalidTypeException;
  }

  private static void setValue(String expressionToShow, ExpressionEvaluator evaluator, EvaluationContextImpl evaluationContext, SetValueRunnable setValueRunnable) throws EvaluateException {
    Value value;
    try {
      value = evaluator.evaluate(evaluationContext);

      setValueRunnable.setValue(evaluationContext, value);
    }
    catch (IllegalArgumentException ex) {
      throw EvaluateExceptionUtil.createEvaluateException(ex.getMessage());
    }
    catch (InvalidTypeException ex) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.type.mismatch"));
    }
    catch (IncompatibleThreadStateException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
    catch (ClassNotLoadedException ex) {
      if (!evaluationContext.isAutoLoadClasses()) {
        throw EvaluateExceptionUtil.createEvaluateException(ex);
      }
      final ReferenceType refType;
      try {
        refType = setValueRunnable.loadClass(evaluationContext, ex.className());
        if (refType != null) {
          //try again
          setValue(expressionToShow, evaluator, evaluationContext, setValueRunnable);
        }
      }
      catch (InvocationException | InvalidTypeException | IncompatibleThreadStateException | ClassNotLoadedException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (ObjectCollectedException e) {
        throw EvaluateExceptionUtil.OBJECT_WAS_COLLECTED;
      }
    }
  }

  protected void set(@NotNull final String expression, final XModificationCallback callback, final DebuggerContextImpl debuggerContext, final SetValueRunnable setValueRunnable) {
    final ProgressWindow progressWindow = new ProgressWindow(true, debuggerContext.getProject());
    final EvaluationContextImpl evaluationContext = myJavaValue.getEvaluationContext();

    SuspendContextCommandImpl askSetAction = new DebuggerContextCommandImpl(debuggerContext) {
      public Priority getPriority() {
        return Priority.HIGH;
      }

      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
        ExpressionEvaluator evaluator;
        try {
          Project project = evaluationContext.getProject();
          SourcePosition position = ContextUtil.getSourcePosition(evaluationContext);
          PsiElement context = ContextUtil.getContextElement(evaluationContext, position);
          evaluator = DebuggerInvocationUtil.commitAndRunReadAction(project, new EvaluatingComputable<ExpressionEvaluator>() {
              public ExpressionEvaluator compute() throws EvaluateException {
                return EvaluatorBuilderImpl
                  .build(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expression), context, position, project);
              }
            });


          setValue(expression, evaluator, evaluationContext, new SetValueRunnable() {
            public void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException,
                                                                                                 InvalidTypeException,
                                                                                                 EvaluateException,
                                                                                                 IncompatibleThreadStateException {
              if (!progressWindow.isCanceled()) {
                setValueRunnable.setValue(evaluationContext, newValue);
                //node.calcValue();
              }
            }

            public ReferenceType loadClass(EvaluationContextImpl evaluationContext, String className) throws
                                                                                                      InvocationException,
                                                                                                      ClassNotLoadedException,
                                                                                                      EvaluateException,
                                                                                                      IncompatibleThreadStateException,
                                                                                                      InvalidTypeException {
              return setValueRunnable.loadClass(evaluationContext, className);
            }
          });
          callback.valueModified();
        } catch (EvaluateException e) {
          callback.errorOccurred(e.getMessage());
        }
        //String initialString = "";
        //if (descriptor instanceof ValueDescriptorImpl) {
        //  Value currentValue = ((ValueDescriptorImpl) descriptor).getValue();
        //  if (currentValue instanceof StringReference) {
        //    initialString = DebuggerUtilsEx.getValueOrErrorAsString(debuggerContext.createEvaluationContext(), currentValue);
        //    initialString = initialString == null ? "" : "\"" + DebuggerUtilsEx.translateStringValue(initialString) + "\"";
        //  }
        //  else if (currentValue instanceof PrimitiveValue) {
        //    ValueLabelRenderer renderer = ((ValueDescriptorImpl) descriptor).getRenderer(debuggerContext.getDebugProcess());
        //    initialString = getDisplayableString((PrimitiveValue) currentValue, renderer instanceof NodeRenderer && HexRenderer.UNIQUE_ID.equals(renderer.getUniqueId()));
        //  }
        //
        //  final String initialString1 = initialString;
        //  final Project project = debuggerContext.getProject();
        //  DebuggerInvocationUtil.swingInvokeLater(project, new Runnable() {
        //    public void run() {
        //      showEditor(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, initialString1), node, debuggerContext, setValueRunnable);
        //    }
        //  });
        //}
      }
    };

    progressWindow.setTitle(DebuggerBundle.message("title.evaluating"));
    evaluationContext.getDebugProcess().getManagerThread().startProgress(askSetAction, progressWindow);
  }

  //private void showEditor(final TextWithImports initialString,
  //                        final DebuggerTreeNodeImpl node,
  //                        final DebuggerContextImpl debuggerContext,
  //                        final SetValueRunnable setValueRunnable) {
  //  final JPanel editorPanel = new JPanel();
  //  editorPanel.setLayout(new BoxLayout(editorPanel, BoxLayout.X_AXIS));
  //  SimpleColoredComponent label = new SimpleColoredComponent();
  //  label.setIcon(node.getIcon());
  //  DebuggerTreeRenderer.getDescriptorTitle(debuggerContext, node.getDescriptor()).appendToComponent(label);
  //  editorPanel.add(label);
  //
  //  final DebuggerExpressionComboBox comboBox = new DebuggerExpressionComboBox(
  //    debuggerContext.getProject(),
  //    PositionUtil.getContextElement(debuggerContext),
  //    "setValue", DefaultCodeFragmentFactory.getInstance());
  //  comboBox.setText(initialString);
  //  comboBox.selectAll();
  //  editorPanel.add(comboBox);
  //
  //  final DebuggerTreeInplaceEditor editor = new DebuggerTreeInplaceEditor(node) {
  //    public JComponent createInplaceEditorComponent() {
  //      return editorPanel;
  //    }
  //
  //    public JComponent getPreferredFocusedComponent() {
  //      return comboBox;
  //    }
  //
  //    public Editor getEditor() {
  //      return comboBox.getEditor();
  //    }
  //
  //    public JComponent getEditorComponent() {
  //      return comboBox.getEditorComponent();
  //    }
  //
  //    private void flushValue() {
  //      if (comboBox.isPopupVisible()) {
  //        comboBox.selectPopupValue();
  //      }
  //
  //      Editor editor = getEditor();
  //      if(editor == null) {
  //        return;
  //      }
  //
  //      final TextWithImports text = comboBox.getText();
  //
  //      PsiFile psiFile = PsiDocumentManager.getInstance(debuggerContext.getProject()).getPsiFile(editor.getDocument());
  //
  //      final ProgressWindowWithNotification progressWindow = new ProgressWindowWithNotification(true, getProject());
  //      EditorEvaluationCommand evaluationCommand = new EditorEvaluationCommand(getEditor(), psiFile, debuggerContext, progressWindow) {
  //        public void threadAction() {
  //          try {
  //            evaluate();
  //          }
  //          catch(EvaluateException e) {
  //            progressWindow.cancel();
  //          }
  //          catch(ProcessCanceledException e) {
  //            progressWindow.cancel();
  //          }
  //          finally{
  //            if (!progressWindow.isCanceled()) {
  //              DebuggerInvocationUtil.swingInvokeLater(debuggerContext.getProject(), new Runnable() {
  //                public void run() {
  //                  comboBox.addRecent(text);
  //                  cancelEditing();
  //                }
  //              });
  //            }
  //          }
  //        }
  //
  //        protected Object evaluate(final EvaluationContextImpl evaluationContext) throws EvaluateException {
  //          ExpressionEvaluator evaluator = DebuggerInvocationUtil.commitAndRunReadAction(evaluationContext.getProject(), new com.intellij.debugger.EvaluatingComputable<ExpressionEvaluator>() {
  //            public ExpressionEvaluator compute() throws EvaluateException {
  //              return EvaluatorBuilderImpl.build(text, ContextUtil.getContextElement(evaluationContext), ContextUtil.getSourcePosition(evaluationContext));
  //            }
  //          });
  //
  //          setValue(text.getText(), evaluator, evaluationContext, new SetValueRunnable() {
  //            public void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException,
  //                                                                                                 InvalidTypeException,
  //                                                                                                 EvaluateException,
  //                                                                                                 IncompatibleThreadStateException {
  //              if (!progressWindow.isCanceled()) {
  //                setValueRunnable.setValue(evaluationContext, newValue);
  //                node.calcValue();
  //              }
  //            }
  //
  //            public ReferenceType loadClass(EvaluationContextImpl evaluationContext, String className) throws
  //                                                                                                      InvocationException,
  //                                                                                                      ClassNotLoadedException,
  //                                                                                                      EvaluateException,
  //                                                                                                      IncompatibleThreadStateException,
  //                                                                                                      InvalidTypeException {
  //              return setValueRunnable.loadClass(evaluationContext, className);
  //            }
  //          });
  //
  //          return null;
  //        }
  //      };
  //
  //      progressWindow.addListener(new ProgressIndicatorListenerAdapter() {
  //        //should return whether to stop processing
  //        public void stopped() {
  //          if(!progressWindow.isCanceled()) {
  //            IJSwingUtilities.invoke(new Runnable() {
  //              public void run() {
  //                cancelEditing();
  //              }
  //            });
  //          }
  //        }
  //
  //
  //      });
  //
  //      progressWindow.setTitle(DebuggerBundle.message("progress.set.value"));
  //      debuggerContext.getDebugProcess().getManagerThread().startProgress(evaluationCommand, progressWindow);
  //    }
  //
  //    public void cancelEditing() {
  //      try {
  //        super.cancelEditing();
  //      }
  //      finally {
  //        comboBox.dispose();
  //      }
  //    }
  //
  //    public void doOKAction() {
  //      try {
  //        flushValue();
  //      }
  //      finally {
  //        comboBox.dispose();
  //      }
  //    }
  //
  //  };
  //
  //  final DebuggerStateManager stateManager = DebuggerManagerEx.getInstanceEx(debuggerContext.getProject()).getContextManager();
  //
  //  stateManager.addListener(new DebuggerContextListener() {
  //    public void changeEvent(DebuggerContextImpl newContext, int event) {
  //      if (event != DebuggerSession.EVENT_THREADS_REFRESH) {
  //        stateManager.removeListener(this);
  //        editor.cancelEditing();
  //      }
  //    }
  //  });
  //
  //  node.getTree().hideTooltip();
  //
  //  editor.show();
  //}

  @SuppressWarnings({"HardCodedStringLiteral", "StringToUpperCaseOrToLowerCaseWithoutLocale"})
  private static String getDisplayableString(PrimitiveValue value, boolean showAsHex) {
    if (value instanceof CharValue) {
      long longValue = value.longValue();
      return showAsHex ? "0x" + Long.toHexString(longValue).toUpperCase() : Long.toString(longValue);
    }
    if (value instanceof ByteValue) {
      byte val = value.byteValue();
      String strValue = Integer.toHexString(val).toUpperCase();
      if (strValue.length() > 2) {
        strValue = strValue.substring(strValue.length() - 2);
      }
      return showAsHex ? "0x" + strValue : value.toString();
    }
    if (value instanceof ShortValue) {
      short val = value.shortValue();
      String strValue = Integer.toHexString(val).toUpperCase();
      if (strValue.length() > 4) {
        strValue = strValue.substring(strValue.length() - 4);
      }
      return showAsHex ? "0x" + strValue : value.toString();
    }
    if (value instanceof IntegerValue) {
      int val = value.intValue();
      return showAsHex ? "0x" + Integer.toHexString(val).toUpperCase() : value.toString();
    }
    if (value instanceof LongValue) {
      long val = value.longValue();
      return showAsHex ? "0x" + Long.toHexString(val).toUpperCase() + "L" : value.toString() + "L";
    }
    return DebuggerUtils.translateStringValue(value.toString());
  }

}
