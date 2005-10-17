package com.intellij.debugger.engine.evaluation.expression;

import com.sun.jdi.ArrayReference;

/**
 * User: lex
 * Date: Oct 16, 2003
 * Time: 2:44:38 PM
 */
public interface InspectArrayItem extends InspectEntity{
  ArrayReference getArray    ();
  int            getItemIndex();
}
