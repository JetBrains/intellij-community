// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.debugger.ui.tree.render.OnDemandRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.sun.jdi.InconsistentDebugInfoException;
import com.sun.jdi.InvalidStackFrameException;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.VMDisconnectedException;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public abstract class NodeDescriptorImpl implements NodeDescriptor {
  protected static final Logger LOG = Logger.getInstance(NodeDescriptorImpl.class);

  public static final String UNKNOWN_VALUE_MESSAGE = "";
  public boolean myIsExpanded = false;
  public boolean myIsSelected = false;
  public boolean myIsVisible = false;

  private EvaluateException myEvaluateException;
  private @NlsContexts.Label String myLabel = UNKNOWN_VALUE_MESSAGE;

  private Map<Key, Object> myUserData;

  @Override
  public String getName() {
    return null;
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    if (myUserData == null) {
      return null;
    }
    //noinspection unchecked
    return (T)myUserData.get(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, T value) {
    if (myUserData == null) {
      myUserData = new HashMap<>();
    }
    myUserData.put(key, value);
  }

  public void updateRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) {
    updateRepresentationNoNotify(context, labelListener);
    labelListener.labelChanged();
  }

  public void updateRepresentationNoNotify(EvaluationContextImpl context, DescriptorLabelListener labelListener) {
    try {
      try {
        myEvaluateException = null;
        myLabel = calcRepresentation(context, labelListener);
      }
      catch (InconsistentDebugInfoException e) {
        throw new EvaluateException(JavaDebuggerBundle.message("error.inconsistent.debug.info"));
      }
      catch (InvalidStackFrameException e) {
        throw new EvaluateException(JavaDebuggerBundle.message("error.invalid.stackframe"));
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
        if (context != null && context.getVirtualMachineProxy().canBeModified()) { // do not care in read only vms
          LOG.debug(e);
        }
        else {
          LOG.warn(e);
        }
        throw new EvaluateException(JavaDebuggerBundle.message("internal.debugger.error"));
      }
    }
    catch (EvaluateException e) {
      setFailed(e);
    }
  }

  protected abstract @NlsContexts.Label String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) throws EvaluateException;

  @Override
  public void displayAs(NodeDescriptor descriptor) {
    if (descriptor instanceof NodeDescriptorImpl that) {
      myIsExpanded = that.myIsExpanded;
      myIsSelected = that.myIsSelected;
      myIsVisible = that.myIsVisible;
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
  public @NlsContexts.Label String getLabel() {
    return myLabel;
  }

  public String toString() {
    return getLabel();
  }

  protected String setFailed(EvaluateException e) {
    myEvaluateException = e;
    return e.getMessage();
  }

  protected String setLabel(@NlsContexts.Label String customLabel) {
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
}
