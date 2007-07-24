package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;

public class MessageDescriptor extends NodeDescriptorImpl {
  public static final int ERROR = 0;
  public static final int WARNING = 1;
  public static final int INFORMATION = 2;
  public static final int SPECIAL = 3;
  private int myKind;
  private String myMessage;

  public static MessageDescriptor DEBUG_INFO_UNAVAILABLE = new MessageDescriptor(DebuggerBundle.message("message.node.debug.info.not.available"));
  public static MessageDescriptor LOCAL_VARIABLES_INFO_UNAVAILABLE = new MessageDescriptor(
    DebuggerBundle.message("message.node.local.variables.debug.info.not.available")
  );
  public static MessageDescriptor ALL_ELEMENTS_IN_VISIBLE_RANGE_ARE_NULL = new MessageDescriptor(
    DebuggerBundle.message("message.node.all.array.elements.null"));
  public static MessageDescriptor ALL_ELEMENTS_IN_RANGE_ARE_NULL = new MessageDescriptor(
    DebuggerBundle.message("message.node.all.elements.null"));
  public static MessageDescriptor ARRAY_IS_EMPTY = new MessageDescriptor(DebuggerBundle.message("message.node.empty.array"));
  public static MessageDescriptor CLASS_HAS_NO_FIELDS = new MessageDescriptor(DebuggerBundle.message("message.node.class.has.no.fields"));
  public static MessageDescriptor OBJECT_COLLECTED = new MessageDescriptor(DebuggerBundle.message("message.node.object.collected"));
  public static MessageDescriptor EVALUATING = new MessageDescriptor(NodeDescriptorImpl.EVALUATING_MESSAGE);
  public static MessageDescriptor THREAD_IS_RUNNING = new MessageDescriptor(DebuggerBundle.message("message.node.thread.running"));
  public static MessageDescriptor THREAD_IS_EMPTY = new MessageDescriptor(DebuggerBundle.message("message.node.thread.has.no.frames"));
  public static MessageDescriptor EVALUATION_NOT_POSSIBLE = new MessageDescriptor(DebuggerBundle.message("message.node.evaluation.not.possible", WARNING));

  public MessageDescriptor(String message) {
    this(message, INFORMATION);
  }

  public MessageDescriptor(String message, int kind) {
    myKind = kind;
    myMessage = message;
  }

  public int getKind() {
    return myKind;
  }

  public String getLabel() {
    return myMessage;
  }

  public boolean isExpandable() {
    return false;
  }

  public void setContext(EvaluationContextImpl context) {
  }

  protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return myMessage;
  }
}