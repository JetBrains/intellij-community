package com.intellij.debugger.ui;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: May 28, 2003
 * Time: 1:59:27 PM
 * To change this template use Options | File Templates.
 */
public interface EvaluatorRunnable extends Runnable{
  Object getValue();
}
