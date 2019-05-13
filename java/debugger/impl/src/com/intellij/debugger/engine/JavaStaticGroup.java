// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.debugger.ui.tree.render.ClassRenderer;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.frame.*;
import com.sun.jdi.Field;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author egor
 */
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

          final ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
          for (Field field : fields) {
            if (field.isStatic()) {
              boolean isSynthetic = DebuggerUtils.isSynthetic(field);
              if (!classRenderer.SHOW_SYNTHETICS && isSynthetic) {
                continue;
              }
              final FieldDescriptorImpl fieldDescriptor = myNodeManager.getFieldDescriptor(myStaticDescriptor, null, field);
              children.add(JavaValue.create(fieldDescriptor, myEvaluationContext, myNodeManager));
              //final DebuggerTreeNodeImpl node = myNodeManager.createNode(fieldDescriptor, myEvaluationContext);
              //myChildren.add(node);
            }
          }

          node.addChildren(children, true);
        }
    });
  }
}
