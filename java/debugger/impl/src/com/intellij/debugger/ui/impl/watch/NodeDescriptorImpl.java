// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.codeInspection.SmartHashMap;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.debugger.ui.tree.render.OnDemandRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.sun.jdi.*;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public abstract class NodeDescriptorImpl implements NodeDescriptor {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl");

  public static final String UNKNOWN_VALUE_MESSAGE = "";
  public boolean myIsExpanded = false;
  public boolean myIsSelected = false;
  public boolean myIsVisible  = false;
  public boolean myIsSynthetic = false;

  private EvaluateException myEvaluateException;
  private String myLabel = UNKNOWN_VALUE_MESSAGE;

  private Map<Key, Object> myUserData;

  private static final Key<Map<ObjectReference, ValueMarkup>> MARKUP_MAP_KEY = new Key<>("ValueMarkupMap");

  @Override
  public String getName() {
    return null;
  }

  @Override
  public <T> T getUserData(Key<T> key) {
    if (myUserData == null) {
      return null;
    }
    //noinspection unchecked
    return (T)myUserData.get(key);
  }

  @Override
  public <T> void putUserData(Key<T> key, T value) {
    if(myUserData == null) {
      myUserData = new SmartHashMap<>();
    }
    myUserData.put(key, value);
  }

  public void updateRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener){
    updateRepresentationNoNotify(context, labelListener);
    labelListener.labelChanged();
  }

  protected void updateRepresentationNoNotify(EvaluationContextImpl context, DescriptorLabelListener labelListener) {
    try {
      try {
        myEvaluateException = null;
        myLabel = calcRepresentation(context, labelListener);
      }
      catch (InconsistentDebugInfoException e) {
        throw new EvaluateException(DebuggerBundle.message("error.inconsistent.debug.info"));
      }
      catch (InvalidStackFrameException e) {
        throw new EvaluateException(DebuggerBundle.message("error.invalid.stackframe"));
      }
      catch (ObjectCollectedException e) {
        throw EvaluateExceptionUtil.OBJECT_WAS_COLLECTED;
      }
      catch (VMDisconnectedException e) {
        throw e;
      }
      catch (RuntimeException e) {
        if (e.getCause() instanceof InterruptedException) {
          throw e;
        }
        LOG.error(e);
        throw new EvaluateException("Internal error, see logs for more details");
      }
    }
    catch (EvaluateException e) {
      setFailed(e);
    }
  }

  protected abstract String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) throws EvaluateException;

  @Override
  public void displayAs(NodeDescriptor descriptor) {
    if (descriptor instanceof NodeDescriptorImpl) {
      final NodeDescriptorImpl that = (NodeDescriptorImpl)descriptor;
      myIsExpanded = that.myIsExpanded;
      myIsSelected = that.myIsSelected;
      myIsVisible  = that.myIsVisible;
      myUserData = that.myUserData != null ? new HashMap<>(that.myUserData) : null;

      // TODO introduce unified way to handle this
      if (myUserData != null) {
        myUserData.remove(OnDemandRenderer.ON_DEMAND_CALCULATED); // calculated flag should not be inherited
      }
    }
  }

  public abstract boolean isExpandable();

  public abstract void setContext(EvaluationContextImpl context);

  public EvaluateException getEvaluateException() {
    return myEvaluateException;
  }

  @Override
  public String getLabel() {
    return myLabel;
  }

  public String toString() {
    return getLabel();
  }

  protected String setFailed(EvaluateException e) {
    myEvaluateException = e;
    return e.getMessage();
  }

  protected String setLabel(String customLabel) {
    return myLabel = customLabel;
  }

  //Context is set to null
  public void clear() {
    myEvaluateException = null;
    myLabel = "";
  }

  @Override
  public void setAncestor(NodeDescriptor oldDescriptor) {
    displayAs(oldDescriptor);
  }

  @Nullable
  public static Map<ObjectReference, ValueMarkup> getMarkupMap(final DebugProcess process) {
    if (process == null) {
      return null;
    }
    Map<ObjectReference, ValueMarkup> map = process.getUserData(MARKUP_MAP_KEY);
    if (map == null) {
      map = new HashMap<>();
      process.putUserData(MARKUP_MAP_KEY, map);
    }
    return map;
  }
}
