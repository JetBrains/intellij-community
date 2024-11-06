// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.actions.ArrayAction;
import com.intellij.debugger.collections.visualizer.CollectionVisualizerEvaluator;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.evaluation.expression.UnBoxingEvaluator;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.memory.utils.ErrorsValueGroup;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.settings.ViewsGeneralSettings;
import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.NodeDescriptorFactory;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.*;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ArrayRenderer extends NodeRendererImpl implements FullValueEvaluatorProvider {
  private static final Logger LOG = Logger.getInstance(ArrayRenderer.class);

  public static final @NonNls String UNIQUE_ID = "ArrayRenderer";

  public int START_INDEX = 0;
  public int END_INDEX = Integer.MAX_VALUE;
  public int ENTRIES_LIMIT = XCompositeNode.MAX_CHILDREN_TO_SHOW;

  private boolean myForced = false;

  public ArrayRenderer() {
    super(DEFAULT_NAME, true);
  }

  @Override
  public String getUniqueId() {
    return UNIQUE_ID;
  }

  @Override
  public @NonNls String getName() {
    return "Array";
  }

  @Override
  public void setName(String text) {
    LOG.assertTrue(false);
  }

  @Override
  public ArrayRenderer clone() {
    return (ArrayRenderer)super.clone();
  }

  @Override
  public @Nullable XFullValueEvaluator getFullValueEvaluator(@NotNull EvaluationContextImpl evaluationContext,
                                                             @NotNull ValueDescriptorImpl valueDescriptor) {
    return CollectionVisualizerEvaluator.createFor(evaluationContext, valueDescriptor);
  }

  @Override
  public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener)
    throws EvaluateException {
    if (!Registry.is("debugger.renderers.arrays") ||
        OnDemandRenderer.isOnDemandForced((DebugProcessImpl)evaluationContext.getDebugProcess())) {
      return ClassRenderer.calcLabel(descriptor, evaluationContext);
    }

    Value value = descriptor.getValue();
    if (value == null) {
      return "null";
    }
    else if (value instanceof ArrayReference arrValue) {
      String componentTypeName = ((ArrayType)arrValue.type()).componentTypeName();
      boolean isString = CommonClassNames.JAVA_LANG_STRING.equals(componentTypeName);
      if (TypeConversionUtil.isPrimitive(componentTypeName) || UnBoxingEvaluator.isTypeUnboxable(componentTypeName) || isString) {
        CompletableFuture<String> asyncLabel = DebuggerUtilsAsync.length(arrValue)
          .thenCompose(length -> {
            if (length > 0) {
              int shownLength = Math.min(length, Registry.intValue(
                isString ? "debugger.renderers.arrays.max.strings" : "debugger.renderers.arrays.max.primitives"));
              return DebuggerUtilsAsync.getValues(arrValue, 0, shownLength).thenCompose(values -> {
                @SuppressWarnings("unchecked")
                CompletableFuture<String>[] futures = ContainerUtil.map2Array(values, new CompletableFuture[0], v -> {
                  if (v != null) {
                    try {
                      v = (Value)UnBoxingEvaluator.unbox(v, evaluationContext);
                    }
                    catch (EvaluateException e) {
                      throw new RuntimeException(e);
                    }
                  }
                  return getElementAsString(v);
                });
                return CompletableFuture.allOf(futures).thenApply(__ -> {
                  List<String> elements = ContainerUtil.map(futures, CompletableFuture::join);
                  if (descriptor instanceof ValueDescriptorImpl) {
                    int compactLength = Math.min(shownLength, isString ? 5 : 10);
                    String compact =
                      createLabel(elements.subList(0, compactLength), length - values.size() + (shownLength - compactLength));
                    ((ValueDescriptorImpl)descriptor).setCompactValueLabel(compact);
                  }
                  return createLabel(elements, length - values.size());
                });
              });
            }
            return CompletableFuture.completedFuture("[]");
          });
        if (asyncLabel.isDone()) {
          return asyncLabel.join();
        }
        else {
          asyncLabel.thenAccept(res -> {
            descriptor.setValueLabel(res);
            listener.labelChanged();
          });
        }
      }
      return "";
    }
    return JavaDebuggerBundle.message("label.undefined");
  }

  private static String createLabel(List<String> elements, int remaining) {
    StreamEx<String> strings = StreamEx.of(elements);
    if (remaining > 0) {
      strings = strings.append(JavaDebuggerBundle.message("message.node.array.elements.more", remaining));
    }
    return strings.joining(", ", "[", "]");
  }

  private static CompletableFuture<String> getElementAsString(Value value) {
    if (value instanceof StringReference) {
      return DebuggerUtilsAsync.getStringValue((StringReference)value).thenApply(e -> "\"" + StringUtil.first(e, 15, true) + "\"");
    }
    return CompletableFuture.completedFuture(value != null ? value.toString() : "null");
  }

  public void setForced(boolean forced) {
    myForced = forced;
  }

  @Override
  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    ArrayReference array = (ArrayReference)value;
    DebuggerUtilsAsync.length(array)
      .thenAccept(arrayLength -> {
        if (arrayLength > 0) {
          if (!myForced) {
            builder.initChildrenArrayRenderer(this, arrayLength);
          }

          if (ENTRIES_LIMIT <= 0) {
            ENTRIES_LIMIT = 1;
          }

          AtomicInteger added = new AtomicInteger();
          AtomicBoolean hiddenNulls = new AtomicBoolean();

          addChunk(array, START_INDEX, Math.min(arrayLength - 1, END_INDEX), arrayLength, builder, evaluationContext, added, hiddenNulls);
        }
      });
  }

  private CompletableFuture<Void> addChunk(ArrayReference array,
                                           int start,
                                           int end,
                                           int length,
                                           ChildrenBuilder builder,
                                           EvaluationContext evaluationContext,
                                           AtomicInteger added,
                                           AtomicBoolean hiddenNulls) {
    int chunkLength = Math.min(XCompositeNode.MAX_CHILDREN_TO_SHOW, end - start + 1);
    return DebuggerUtilsAsync.getValues(array, start, chunkLength)
      .thenCompose(values -> {
        int idx = start;
        for (; idx < start + values.size(); idx++) {
          Value val = values.get(idx - start);
          if (ViewsGeneralSettings.getInstance().HIDE_NULL_ARRAY_ELEMENTS && val == null) {
            hiddenNulls.set(true);
            continue;
          }

          ArrayElementDescriptorImpl descriptor = (ArrayElementDescriptorImpl)builder.getDescriptorManager()
            .getArrayItemDescriptor(builder.getParentDescriptor(), array, idx);
          descriptor.setValue(val);
          DebuggerTreeNode arrayItemNode = ((NodeManagerImpl)builder.getNodeManager()).createNode(descriptor, evaluationContext);
          builder.addChildren(Collections.singletonList(arrayItemNode), false);

          if (added.incrementAndGet() >= ENTRIES_LIMIT) {
            break;
          }
        }
        // process next chunk if needed
        if (idx < end && added.get() < ENTRIES_LIMIT) {
          return addChunk(array, idx, end, length, builder, evaluationContext, added, hiddenNulls);
        }
        finish(builder, length, added.get(), hiddenNulls.get(), end, idx);
        return CompletableFuture.completedFuture(null);
      });
  }

  private void finish(ChildrenBuilder builder, int arrayLength, int added, boolean hiddenNulls, int end, int idx) {
    builder.addChildren(Collections.emptyList(), true);

    if (added == 0) {
      if (START_INDEX == 0 && arrayLength - 1 <= END_INDEX) {
        builder
          .setMessage(JavaDebuggerBundle.message("message.node.all.elements.null"), null, SimpleTextAttributes.REGULAR_ATTRIBUTES,
                      null);
      }
      else {
        builder.setMessage(JavaDebuggerBundle.message("message.node.all.array.elements.null", START_INDEX, END_INDEX), null,
                           SimpleTextAttributes.REGULAR_ATTRIBUTES, null);
      }
    }
    else {
      if (hiddenNulls) {
        builder
          .setMessage(JavaDebuggerBundle.message("message.node.elements.null.hidden"), null, SimpleTextAttributes.REGULAR_ATTRIBUTES,
                      null);
      }
      if (!myForced && idx < end) {
        builder.tooManyChildren(end - idx);
      }
    }
  }

  private static final class ArrayValuesCache {
    private final ArrayReference myArray;
    private List<Value> myCachedValues = Collections.emptyList();
    private int myCachedStartIndex;

    private ArrayValuesCache(ArrayReference array) {
      myArray = array;
    }

    Value getValue(int index) {
      if (index < myCachedStartIndex || index >= myCachedStartIndex + myCachedValues.size()) {
        myCachedStartIndex = index;
        myCachedValues = myArray.getValues(index, Math.min(XCompositeNode.MAX_CHILDREN_TO_SHOW, myArray.length() - index));
      }
      return myCachedValues.get(index - myCachedStartIndex);
    }
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  @Override
  public PsiExpression getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) {
    LOG.assertTrue(node.getDescriptor() instanceof ArrayElementDescriptorImpl, node.getDescriptor().getClass().getName());
    ArrayElementDescriptorImpl descriptor = (ArrayElementDescriptorImpl)node.getDescriptor();

    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(node.getProject());
    try {
      LanguageLevel languageLevel = LanguageLevelProjectExtension.getInstance(node.getProject()).getLanguageLevel();
      return elementFactory.createExpressionFromText("this[" + descriptor.getIndex() + "]", elementFactory.getArrayClass(languageLevel));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  @Override
  public CompletableFuture<Boolean> isExpandableAsync(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    if (!(value instanceof ArrayReference)) {
      return CompletableFuture.completedFuture(false);
    }
    return DebuggerUtilsAsync.length(((ArrayReference)value)).thenApply(l -> l > 0);
  }

  @Override
  public boolean isApplicable(Type type) {
    return type instanceof ArrayType;
  }

  //TODO: make async
  public static class Filtered extends ArrayRenderer {
    private final XExpression myExpression;

    public Filtered(XExpression expression) {
      myExpression = expression;
    }

    public XExpression getExpression() {
      return myExpression;
    }

    @Override
    public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
      DebuggerManagerThreadImpl.assertIsManagerThread();
      NodeManagerImpl nodeManager = (NodeManagerImpl)builder.getNodeManager();
      NodeDescriptorFactory descriptorFactory = builder.getDescriptorManager();

      builder.setMessage(JavaDebuggerBundle.message("message.node.filtered") + " " + myExpression.getExpression(),
                         AllIcons.General.Filter,
                         SimpleTextAttributes.REGULAR_ATTRIBUTES,
                         FILTER_HYPERLINK);

      if (ENTRIES_LIMIT <= 0) {
        ENTRIES_LIMIT = 1;
      }

      ArrayReference array = (ArrayReference)value;
      int arrayLength = array.length();
      if (arrayLength > 0) {
        builder.initChildrenArrayRenderer(this, arrayLength);

        CachedEvaluator cachedEvaluator = new CachedEvaluator() {
          @Override
          protected String getClassName() {
            return ((ArrayType)array.type()).componentTypeName();
          }

          @Override
          protected PsiElement overrideContext(PsiElement context) {
            return ContextUtil.getContextElement(evaluationContext);
          }
        };
        cachedEvaluator.setReferenceExpression(TextWithImportsImpl.fromXExpression(myExpression));

        try {
          int added = 0;
          if (arrayLength - 1 >= START_INDEX) {

            ErrorsValueGroup errorsGroup = null;
            ArrayValuesCache arrayValuesCache = new ArrayValuesCache(array);
            for (int idx = START_INDEX; idx < arrayLength; idx++) {
              ArrayElementDescriptorImpl descriptor =
                (ArrayElementDescriptorImpl)descriptorFactory.getArrayItemDescriptor(builder.getParentDescriptor(), array, idx);
              Value val = arrayValuesCache.getValue(idx);
              descriptor.setValue(val);
              try {
                if (DebuggerUtilsEx.evaluateBoolean(cachedEvaluator.getEvaluator(evaluationContext.getProject()),
                                                    (EvaluationContextImpl)evaluationContext.createEvaluationContext(val))) {

                  DebuggerTreeNode arrayItemNode = nodeManager.createNode(descriptor, evaluationContext);
                  builder.addChildren(Collections.singletonList(arrayItemNode), false);
                  added++;
                  //if (added > ENTRIES_LIMIT) {
                  //  break;
                  //}
                }
              }
              catch (EvaluateException e) {
                if (errorsGroup == null) {
                  errorsGroup = new ErrorsValueGroup();
                  builder.addChildren(XValueChildrenList.bottomGroup(errorsGroup), false);
                }
                JavaValue childValue = JavaValue.create(null, descriptor, ((EvaluationContextImpl)evaluationContext), nodeManager, false);
                errorsGroup.addErrorValue(e.getMessage(), childValue);
              }
            }
          }

          builder.addChildren(Collections.emptyList(), true);
        }
        catch (ObjectCollectedException e) {
          builder.setErrorMessage(JavaDebuggerBundle.message("evaluation.error.array.collected"));
        }

        //if (added != 0 && END_INDEX < arrayLength - 1) {
        //  builder.setRemaining(arrayLength - 1 - END_INDEX);
        //}
      }
    }

    public static final XDebuggerTreeNodeHyperlink FILTER_HYPERLINK = new XDebuggerTreeNodeHyperlink(
      JavaDebuggerBundle.message("array.filter.node.clear.link")) {
      @Override
      public void onClick(MouseEvent e) {
        XDebuggerTree tree = (XDebuggerTree)e.getSource();
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path != null) {
          TreeNode parent = ((TreeNode)path.getLastPathComponent()).getParent();
          if (parent instanceof XValueNodeImpl valueNode) {
            ArrayAction.setArrayRenderer(NodeRendererSettings.getInstance().getArrayRenderer(),
                                         valueNode,
                                         DebuggerManagerEx.getInstanceEx(tree.getProject()).getContext());
          }
        }
        e.consume();
      }
    };
  }
}
