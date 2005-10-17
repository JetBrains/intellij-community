package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.openapi.project.Project;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 2, 2004
 * Time: 3:50:51 PM
 * To change this template use File | Settings | File Templates.
 */
public interface JVMName {
  String getName(DebugProcessImpl process) throws EvaluateException;
  String getDisplayName(DebugProcessImpl debugProcess);
}
