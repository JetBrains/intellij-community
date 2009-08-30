package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.sun.jdi.LocalVariable;

/**
 * User: lex
 * Date: Oct 15, 2003
 * Time: 11:39:04 PM
 *
 * todo [lex] does this interface really required?
 */
public interface InspectLocal extends InspectEntity{
  StackFrameProxyImpl getStackFrame();
  LocalVariable   getLocal     ();
}
