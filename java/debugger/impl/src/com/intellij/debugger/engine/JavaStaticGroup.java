// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import com.sun.jdi.Field;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;

public class JavaStaticGroup extends XValueGroup implements NodeDescriptorProvider {
  private final StaticDescriptorImpl myStaticDescriptor;
  private final EvaluationContextImpl myEvaluationContext;
  private final NodeManagerImpl myNodeManager;

  public JavaStaticGroup(StaticDescriptorImpl staticDescriptor,
                         EvaluationContextImpl evaluationContext,
                         NodeManagerImpl nodeManager) {
    super(staticDescriptor.getName());
    myStaticDescriptor = staticDescriptor;
    myEvaluationContext = evaluationContext;
    myNodeManager = nodeManager;
  }

  @Nullable
  @Override
  public String getComment() {
    String res = NodeRendererSettings.getInstance().getClassRenderer().renderTypeName(myStaticDescriptor.getType().name());
    if (!StringUtil.isEmpty(res)) {
      return " members of " + res;
    }
    return res;
  }

  @NotNull
  @Override
  public String getSeparator() {
    return "";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.Static;
  }

  @Override
  public NodeDescriptorImpl getDescriptor() {
    return myStaticDescriptor;
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    JavaValue.scheduleCommand(myEvaluationContext, node, new SuspendContextCommandImpl(myEvaluationContext.getSuspendContext()) {
        @Override
        public void contextAction(@NotNull SuspendContextImpl suspendContext) {
          final XValueChildrenList children = new XValueChildrenList();

          final ReferenceType refType = myStaticDescriptor.getType();
          List<Field> fields = refType.allFields();

          boolean showSynthetics = NodeRendererSettings.getInstance().getClassRenderer().SHOW_SYNTHETICS;
          List<Field> fieldsToShow = ContainerUtil.filter(fields, f -> f.isStatic() && (showSynthetics || !DebuggerUtils.isSynthetic(f)));
          int loaded = 0, total = fieldsToShow.size();
          Map<Field, Value> cachedValues = null;
          for (int i = 0; i < total; i++) {
            Field field = fieldsToShow.get(i);
            // load values in chunks
            if (i > loaded || cachedValues == null) {
              int chunkSize = Math.min(XCompositeNode.MAX_CHILDREN_TO_SHOW, total - loaded);
              try {
                cachedValues = refType.getValues(fieldsToShow.subList(loaded, loaded + chunkSize));
              }
              catch (Exception e) {
                cachedValues = null;
              }
              loaded += chunkSize;
            }
            FieldDescriptorImpl fieldDescriptor = myNodeManager.getFieldDescriptor(myStaticDescriptor, null, field);
            if (cachedValues != null) {
              fieldDescriptor.setValue(cachedValues.get(field));
            }
            children.add(JavaValue.create(fieldDescriptor, myEvaluationContext, myNodeManager));
          }

          node.addChildren(children, true);
        }
    });
  }
}
