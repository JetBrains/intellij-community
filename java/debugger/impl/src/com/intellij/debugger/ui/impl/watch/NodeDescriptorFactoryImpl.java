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
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.jdi.LocalVariableProxy;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import com.intellij.debugger.engine.jdi.ThreadReferenceProxy;
import com.intellij.debugger.impl.descriptors.data.*;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.NodeDescriptorFactory;
import com.intellij.debugger.ui.tree.UserExpressionDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.sun.jdi.*;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class NodeDescriptorFactoryImpl implements NodeDescriptorFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.watch.NodeDescriptorFactoryImpl");
  private DescriptorTree myCurrentHistoryTree = new DescriptorTree(true);

  private DescriptorTreeSearcher myDescriptorSearcher;
  private DescriptorTreeSearcher myDisplayDescriptorSearcher;

  protected final Project      myProject;

  public NodeDescriptorFactoryImpl(Project project) {
    myProject = project;
    myDescriptorSearcher = new DescriptorTreeSearcher(new MarkedDescriptorTree());
    myDisplayDescriptorSearcher = new DisplayDescriptorTreeSearcher(new MarkedDescriptorTree());
  }

  public void dispose() {
    myCurrentHistoryTree.clear();
    myDescriptorSearcher.clear();
    myDisplayDescriptorSearcher.clear();
  }

  private <T extends NodeDescriptor> T getDescriptor(NodeDescriptor parent, DescriptorData<T> key) {
    final T descriptor = key.createDescriptor(myProject);

    final T oldDescriptor = findDescriptor(parent, descriptor, key);

    if(oldDescriptor != null && oldDescriptor.getClass() == descriptor.getClass()) {
      descriptor.setAncestor(oldDescriptor);
    }
    else {
      T displayDescriptor = findDisplayDescriptor(parent, descriptor, key.getDisplayKey());
      if(displayDescriptor != null) {
        descriptor.displayAs(displayDescriptor);
      }
    }

    myCurrentHistoryTree.addChild(parent, descriptor);

    return descriptor;
  }

  @Nullable
  protected <T extends NodeDescriptor>T findDisplayDescriptor(NodeDescriptor parent, T descriptor, DisplayKey<T> key) {
    return myDisplayDescriptorSearcher.search(parent, descriptor, key);
  }

  @Nullable
  protected <T extends NodeDescriptor> T findDescriptor(NodeDescriptor parent, T descriptor, DescriptorData<T> key) {
    return myDescriptorSearcher.search(parent, descriptor, key);
  }

  public DescriptorTree getCurrentHistoryTree() {
    return myCurrentHistoryTree;
  }

  public void deriveHistoryTree(DescriptorTree tree, final StackFrameContext context) {

    final MarkedDescriptorTree descriptorTree = new MarkedDescriptorTree();
    final MarkedDescriptorTree displayDescriptorTree = new MarkedDescriptorTree();

    tree.dfst(new DescriptorTree.DFSTWalker() {
      public void visit(NodeDescriptor parent, NodeDescriptor child) {
        final DescriptorData<NodeDescriptor> descriptorData = DescriptorData.getDescriptorData(child);
        descriptorTree.addChild(parent, child, descriptorData);
        displayDescriptorTree.addChild(parent, child, descriptorData.getDisplayKey());
      }
    });

    myDescriptorSearcher = new DescriptorTreeSearcher(descriptorTree);
    myDisplayDescriptorSearcher = new DisplayDescriptorTreeSearcher(displayDescriptorTree);

    myCurrentHistoryTree = createDescriptorTree(context, tree);
  }

  private static DescriptorTree createDescriptorTree(final StackFrameContext context, final DescriptorTree fromTree) {
    int frameCount = -1;
    int frameIndex = -1;
    final StackFrameProxy frameProxy = context.getFrameProxy();
    if (frameProxy != null) {
      try {
        final ThreadReferenceProxy threadReferenceProxy = frameProxy.threadProxy();
        frameCount = threadReferenceProxy.frameCount();
        frameIndex = frameProxy.getFrameIndex();
       }
       catch (EvaluateException e) {
         // ignored
       }
    }
    final boolean isInitial = !fromTree.frameIdEquals(frameCount, frameIndex);
    DescriptorTree descriptorTree = new DescriptorTree(isInitial);
    descriptorTree.setFrameId(frameCount, frameIndex);
    return descriptorTree;
  }

  public ArrayElementDescriptorImpl getArrayItemDescriptor(NodeDescriptor parent, ArrayReference array, int index) {
    return getDescriptor(parent, new ArrayItemData(array, index));
  }

  public FieldDescriptorImpl getFieldDescriptor(NodeDescriptor parent, ObjectReference objRef, Field field) {
    final DescriptorData<FieldDescriptorImpl> descriptorData;
    if (objRef == null ) {
      if (!field.isStatic()) {
        LOG.error("Object reference is null for non-static field: " + field);
      }
      descriptorData = new StaticFieldData(field);
    }
    else {
      descriptorData = new FieldData(objRef, field);
    }
    return getDescriptor(parent, descriptorData);
  }

  public LocalVariableDescriptorImpl getLocalVariableDescriptor(NodeDescriptor parent, LocalVariableProxy local) {
    return getDescriptor(parent, new LocalData((LocalVariableProxyImpl)local));
  }

  public ArgumentValueDescriptorImpl getArgumentValueDescriptor(NodeDescriptor parent, int index, Value value) {
    return getDescriptor(parent, new ArgValueData(index, value));
  }

  public StackFrameDescriptorImpl getStackFrameDescriptor(NodeDescriptorImpl parent, StackFrameProxyImpl frameProxy) {
    return getDescriptor(parent, new StackFrameData(frameProxy));
  }

  public StaticDescriptorImpl getStaticDescriptor(NodeDescriptorImpl parent, ReferenceType refType) {//static is unique
    return getDescriptor(parent, new StaticData(refType));
  }

  public ValueDescriptorImpl getThisDescriptor(NodeDescriptorImpl parent, Value value) {
    return getDescriptor(parent, new ThisData());
  }

  public ValueDescriptorImpl getMethodReturnValueDescriptor(NodeDescriptorImpl parent, Method method, Value value) {
    return getDescriptor(parent, new MethodReturnValueData(method, value));
  }

  public ThreadDescriptorImpl getThreadDescriptor(NodeDescriptorImpl parent, ThreadReferenceProxyImpl thread) {
    return getDescriptor(parent, new ThreadData(thread));
  }

  public ThreadGroupDescriptorImpl getThreadGroupDescriptor(NodeDescriptorImpl parent, ThreadGroupReferenceProxyImpl group) {
    return getDescriptor(parent, new ThreadGroupData(group));
  }

  public UserExpressionDescriptor getUserExpressionDescriptor(NodeDescriptor parent, final DescriptorData<UserExpressionDescriptor> data) {
    return getDescriptor(parent, data);
  }

  public WatchItemDescriptor getWatchItemDescriptor(NodeDescriptor parent, TextWithImports text, @Nullable Value value){
    return getDescriptor(parent, new WatchItemData(text, value));
  }
  
  private static class DescriptorTreeSearcher {
    private final MarkedDescriptorTree myDescriportTree;

    private final HashMap<NodeDescriptor, NodeDescriptor> mySearchedDescriptors = new HashMap<NodeDescriptor, NodeDescriptor>();

    public DescriptorTreeSearcher(MarkedDescriptorTree descriportTree) {
      myDescriportTree = descriportTree;
    }

    @Nullable
    public <T extends NodeDescriptor> T search(NodeDescriptor parent, T descriptor, DescriptorKey<T> key) {
      final T result;
      if(parent == null) {
        result = myDescriportTree.getChild(null, key);
      }
      else {
        final NodeDescriptor parentDescriptor = getSearched(parent);
        result = parentDescriptor != null ? myDescriportTree.getChild(parentDescriptor, key) : null;
      }
      if(result != null) {
        mySearchedDescriptors.put(descriptor, result);
      }
      return result;
    }

    protected NodeDescriptor getSearched(NodeDescriptor parent) {
      return mySearchedDescriptors.get(parent);
    }

    public void clear() {
      mySearchedDescriptors.clear();
      myDescriportTree.clear();
    }
  }

  private class DisplayDescriptorTreeSearcher extends DescriptorTreeSearcher {
    public DisplayDescriptorTreeSearcher(MarkedDescriptorTree descriportTree) {
      super(descriportTree);
    }

    protected NodeDescriptor getSearched(NodeDescriptor parent) {
      NodeDescriptor searched = super.getSearched(parent);
      if(searched == null) {
        return myDescriptorSearcher.getSearched(parent);
      }
      return searched;
    }
  }
}
