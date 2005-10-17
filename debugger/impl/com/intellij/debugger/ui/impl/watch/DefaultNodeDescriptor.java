/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.openapi.diagnostic.Logger;


public final class DefaultNodeDescriptor extends NodeDescriptorImpl{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.watch.DefaultNodeDescriptor");
  public boolean equals(Object obj) {
    return obj instanceof DefaultNodeDescriptor;
  }

  public int hashCode() {
    return 0;
  }

  public boolean isExpandable() {
    return true;
  }

  public void setContext(EvaluationContextImpl context) {
  }

  protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) {
    LOG.assertTrue(false);
    return null;
  }
}
