/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
