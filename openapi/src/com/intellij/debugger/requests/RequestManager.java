/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.requests;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 2, 2004
 * Time: 7:18:56 PM
 * To change this template use File | Settings | File Templates.
 */
public interface RequestManager {
  public void callbackOnPrepareClasses(ClassPrepareRequestor requestor, String         classOrPatternToBeLoaded);
  public void callbackOnPrepareClasses(ClassPrepareRequestor requestor, SourcePosition classPosition) throws EvaluateException;

  public ClassPrepareRequest createClassPrepareRequest(ClassPrepareRequestor requestor, String pattern);  

  public void enableRequest(EventRequest request);

  public void setInvalid(Requestor requestor, String message);
}
