// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
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
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

  @Override
  public @Nullable String getComment() {
    String res = NodeRendererSettings.getInstance().getClassRenderer().renderTypeName(myStaticDescriptor.getType().name());
    if (!StringUtil.isEmpty(res)) {
      return " members of " + res;
    }
    return res;
  }

  @Override
  public @NotNull String getSeparator() {
    return "";
  }

  @Override
  public @Nullable Icon getIcon() {
    return AllIcons.Nodes.Static;
  }

  @Override
  public NodeDescriptorImpl getDescriptor() {
    return myStaticDescriptor;
  }

  @Override
  public void computeChildren(final @NotNull XCompositeNode node) {
    JavaValue.scheduleCommand(myEvaluationContext, node, new SuspendContextCommandImpl(myEvaluationContext.getSuspendContext()) {
      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        ReferenceType refType = myStaticDescriptor.getType();
        DebuggerUtilsAsync.allFields(refType)
          .thenAccept(
            fields -> {
              boolean showSynthetics = NodeRendererSettings.getInstance().getClassRenderer().SHOW_SYNTHETICS;
              List<Field> fieldsToShow =
                ContainerUtil.filter(fields, f -> f.isStatic() && (showSynthetics || !DebuggerUtils.isSynthetic(f)));
              List<List<Field>> chunks = DebuggerUtilsImpl.partition(fieldsToShow, XCompositeNode.MAX_CHILDREN_TO_SHOW);

              //noinspection unchecked
              CompletableFuture<XValueChildrenList>[] futures = chunks.stream()
                .map(l -> createNodes(l, refType))
                .toArray(CompletableFuture[]::new);
              CompletableFuture.allOf(futures)
                .thenAccept(__ -> {
                  StreamEx.of(futures).map(CompletableFuture::join).forEach(c -> node.addChildren(c, false));
                  node.addChildren(XValueChildrenList.EMPTY, true);
                });
            }
          );
      }

      private CompletableFuture<XValueChildrenList> createNodes(List<Field> fields, ReferenceType refType) {
        return DebuggerUtilsAsync.getValues(refType, fields)
          .thenApply(cachedValues -> {
                       XValueChildrenList children = new XValueChildrenList();
                       for (Field field : fields) {
                         FieldDescriptorImpl fieldDescriptor = myNodeManager.getFieldDescriptor(myStaticDescriptor, null, field);
                         if (cachedValues != null) {
                           fieldDescriptor.setValue(cachedValues.get(field));
                         }
                         children.add(JavaValue.create(fieldDescriptor, myEvaluationContext, myNodeManager));
                       }
                       return children;
                     }
          );
      }
    });
  }
}
