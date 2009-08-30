package com.intellij.debugger.engine.evaluation.expression;

import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;

/**
 * User: lex
 * Date: Oct 15, 2003
 * Time: 11:38:25 PM
 */
public interface InspectField extends InspectEntity{
  ObjectReference getObject();
  Field           getField ();
}
