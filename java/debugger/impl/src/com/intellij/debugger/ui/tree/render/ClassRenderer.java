// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.FieldVisibilityProvider;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.jetbrains.jdi.StringReferenceImpl;
import com.sun.jdi.*;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ClassRenderer extends NodeRendererImpl {
  private static final Logger LOG = Logger.getInstance(ClassRenderer.class);

  public static final @NonNls String UNIQUE_ID = "ClassRenderer";

  public boolean SHOW_SYNTHETICS = true;
  public boolean SHOW_VAL_FIELDS_AS_LOCAL_VARIABLES = true;
  public boolean SHOW_STATIC = false;
  public boolean SHOW_STATIC_FINAL = false;

  public boolean SHOW_FQ_TYPE_NAMES = false;
  public boolean SHOW_DECLARED_TYPE = false;
  public boolean SHOW_OBJECT_ID = true;

  public boolean SHOW_STRINGS_TYPE = false;

  public ClassRenderer() {
    super(DEFAULT_NAME, true);
  }

  @Nullable
  public final String renderTypeName(@Nullable final String typeName) {
    if (SHOW_FQ_TYPE_NAMES || typeName == null) {
      return typeName;
    }
    String baseLambdaClassName = DebuggerUtilsEx.getLambdaBaseClassName(typeName);
    if (baseLambdaClassName != null) {
      return renderTypeName(baseLambdaClassName) + "$lambda";
    }

    final int dotIndex = typeName.lastIndexOf('.');
    if (dotIndex > 0) {
      return typeName.substring(dotIndex + 1);
    }
    return typeName;
  }

  @Override
  public String getUniqueId() {
    return UNIQUE_ID;
  }

  @Override
  public ClassRenderer clone() {
    return (ClassRenderer)super.clone();
  }

  @Override
  public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener)
    throws EvaluateException {
    return calcLabelAsync(descriptor, evaluationContext, labelListener);
  }

  private static String calcLabelAsync(ValueDescriptor descriptor,
                                       EvaluationContext evaluationContext,
                                       DescriptorLabelListener labelListener)
    throws EvaluateException {
    Value value = descriptor.getValue();
    CompletableFuture<String> future;
    if (value instanceof StringReferenceImpl) {
      DebuggerUtils.ensureNotInsideObjectConstructor((ObjectReference)value, evaluationContext);
      future = DebuggerUtilsAsync.getStringValue((StringReference)value);
    }
    else {
      future = CompletableFuture.completedFuture(calcLabel(descriptor, evaluationContext));
    }
    return calcLabelFromFuture(future, descriptor, labelListener);
  }

  private static String calcLabelFromFuture(CompletableFuture<String> future,
                                            ValueDescriptor descriptor,
                                            DescriptorLabelListener labelListener) {
    if (!future.isDone()) {
      future.whenComplete((s, throwable) -> {
        if (throwable != null) {
          descriptor.setValueLabelFailed((EvaluateException)throwable);
        }
        else {
          descriptor.setValueLabel(s);
        }
        labelListener.labelChanged();
      });
    }
    return future.getNow(XDebuggerUIConstants.getCollectingDataMessage());
  }

  protected static String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext) throws EvaluateException {
    Value value = descriptor.getValue();
    if (value instanceof ObjectReference) {
      if (value instanceof StringReference) {
        DebuggerUtils.ensureNotInsideObjectConstructor((ObjectReference)value, evaluationContext);
        return ((StringReference)value).value();
      }
      else if (value instanceof ClassObjectReference) {
        ReferenceType type = ((ClassObjectReference)value).reflectedType();
        return (type != null) ? type.name() : "{...}";
      }
      else {
        final ObjectReference objRef = (ObjectReference)value;
        final Type type = objRef.type();
        if (type instanceof ClassType && ((ClassType)type).isEnum()) {
          final String name = getEnumConstantName(objRef, (ClassType)type);
          if (name != null) {
            return name;
          }
          else {
            return type.name();
          }
        }
        else {
          return "";
        }
      }
    }
    else if (value == null) {
      return "null";
    }
    else {
      return JavaDebuggerBundle.message("label.undefined");
    }
  }

  @Override
  public void buildChildren(final Value value, final ChildrenBuilder builder, final EvaluationContext evaluationContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final ValueDescriptorImpl parentDescriptor = (ValueDescriptorImpl)builder.getParentDescriptor();
    final NodeManager nodeManager = builder.getNodeManager();
    final NodeDescriptorFactory nodeDescriptorFactory = builder.getDescriptorManager();

    if (!(value instanceof ObjectReference objRef)) {
      builder.setChildren(Collections.emptyList());
      return;
    }

    final ReferenceType refType = objRef.referenceType();
    // default ObjectReference processing
    DebuggerUtilsAsync.allFields(refType)
      .thenAccept(fields -> {
          if (fields.isEmpty()) {
            builder.setChildren(Collections.singletonList(nodeManager.createMessageNode(MessageDescriptor.CLASS_HAS_NO_FIELDS.getLabel())));
            return;
          }

          createNodesToShow(fields, evaluationContext, parentDescriptor, nodeManager, nodeDescriptorFactory, objRef)
            .thenAccept(nodesToShow -> {
              if (nodesToShow.isEmpty()) {
                setClassHasNoFieldsToDisplayMessage(builder, nodeManager);
                return;
              }

              builder.setChildren(nodesToShow);
            }
          );
      }
    );
  }

  protected void setClassHasNoFieldsToDisplayMessage(ChildrenBuilder builder, NodeManager nodeManager) {
    builder.setChildren(Collections.singletonList(nodeManager.createMessageNode(JavaDebuggerBundle.message("message.node.class.no.fields.to.display"))));
  }

  protected CompletableFuture<List<DebuggerTreeNode>> createNodesToShow(List<Field> fields,
                                                                        EvaluationContext evaluationContext,
                                                                        ValueDescriptorImpl parentDescriptor,
                                                                        NodeManager nodeManager,
                                                                        NodeDescriptorFactory nodeDescriptorFactory,
                                                                        ObjectReference objRef) {
    List<Field> fieldsToShow = ContainerUtil.filter(fields, field -> shouldDisplay(evaluationContext, objRef, field));
    if (fieldsToShow.isEmpty()) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    CompletableFuture<List<DebuggerTreeNode>>[] futures = createNodesChunked(
      fieldsToShow, evaluationContext, parentDescriptor, nodeManager, nodeDescriptorFactory, objRef
    );

    return CompletableFuture.allOf(futures).thenApply(__ -> StreamEx.of(futures).flatCollection(CompletableFuture::join).toList());
  }

  private CompletableFuture<List<DebuggerTreeNode>>[] createNodesChunked(List<Field> fields,
                                                                         EvaluationContext evaluationContext,
                                                                         ValueDescriptorImpl parentDescriptor,
                                                                         NodeManager nodeManager,
                                                                         NodeDescriptorFactory nodeDescriptorFactory,
                                                                         ObjectReference objRef) {
    List<List<Field>> chunks = DebuggerUtilsImpl.partition(fields, XCompositeNode.MAX_CHILDREN_TO_SHOW);
    Set<String> names = Collections.synchronizedSet(new HashSet<>());
    //noinspection unchecked
    return chunks.stream()
      .map(l -> createNodes(l, evaluationContext, parentDescriptor, nodeManager, nodeDescriptorFactory, objRef, names))
      .toArray(CompletableFuture[]::new);
  }

  private CompletableFuture<List<DebuggerTreeNode>> createNodes(List<Field> fields,
                                                                EvaluationContext evaluationContext,
                                                                ValueDescriptorImpl parentDescriptor,
                                                                NodeManager nodeManager,
                                                                NodeDescriptorFactory nodeDescriptorFactory,
                                                                ObjectReference objRef,
                                                                Set<String> names) {
    return DebuggerUtilsAsync.getValues(objRef, fields)
      .thenApply(cachedValues -> {
        List<DebuggerTreeNode> res = new ArrayList<>(fields.size());
        for (Field field : fields) {
          FieldDescriptorImpl fieldDescriptor =
            (FieldDescriptorImpl)createFieldDescriptor(parentDescriptor, nodeDescriptorFactory, objRef, field, evaluationContext);
          if (cachedValues != null) {
            fieldDescriptor.setValue(cachedValues.get(field));
          }
          if (!names.add(fieldDescriptor.getName())) {
            fieldDescriptor.putUserData(FieldDescriptor.SHOW_DECLARING_TYPE, Boolean.TRUE);
          }
          res.add(nodeManager.createNode(fieldDescriptor, evaluationContext));
        }
        return res;
      });
  }

  @NotNull
  protected FieldDescriptor createFieldDescriptor(ValueDescriptorImpl parentDescriptor,
                                                  NodeDescriptorFactory nodeDescriptorFactory,
                                                  ObjectReference objRef,
                                                  Field field,
                                                  EvaluationContext evaluationContext) {
    return nodeDescriptorFactory.getFieldDescriptor(parentDescriptor, objRef, field);
  }

  protected boolean shouldDisplay(EvaluationContext context, @NotNull ObjectReference objInstance, @NotNull Field field) {
    final boolean isSynthetic = DebuggerUtils.isSynthetic(field);
    if (!SHOW_SYNTHETICS && isSynthetic) {
      return false;
    }
    if (SHOW_VAL_FIELDS_AS_LOCAL_VARIABLES && isSynthetic) {
      try {
        final StackFrameProxy frameProxy = context.getFrameProxy();
        if (frameProxy != null) {
          final Location location = frameProxy.location();
          if (location != null &&
              objInstance.equals(context.computeThisObject()) &&
              Comparing.equal(objInstance.referenceType(), location.declaringType()) &&
              StringUtil.startsWith(field.name(), FieldDescriptorImpl.OUTER_LOCAL_VAR_FIELD_PREFIX)) {
            return false;
          }
        }
      }
      catch (EvaluateException ignored) {
      }
    }
    if (!SHOW_STATIC && field.isStatic()) {
      return false;
    }

    if (!SHOW_STATIC_FINAL && field.isStatic() && field.isFinal()) {
      return false;
    }

    return FieldVisibilityProvider.shouldDisplayField(field);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    DefaultJDOMExternalizer.write(this, element, new DifferenceFilter<>(this, new ClassRenderer()));
  }

  @Override
  public PsiElement getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    DescriptorWithParentObject descriptor = (DescriptorWithParentObject)node.getDescriptor();

    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(node.getProject());
    try {
      return elementFactory.createExpressionFromText("this." + descriptor.getName(), DebuggerUtils.findClass(
        descriptor.getObject().referenceType().name(), context.getProject(), context.getDebugProcess().getSearchScope())
      );
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(JavaDebuggerBundle.message("error.invalid.field.name", descriptor.getName()), null);
    }
  }

  @Override
  public CompletableFuture<Boolean> isExpandableAsync(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (value instanceof ArrayReference) {
      return DebuggerUtilsAsync.length((ArrayReference)value).thenApply(r -> r > 0).exceptionally(throwable -> true);
    }
    else if (value instanceof ObjectReference) {
      return CompletableFuture.completedFuture(true); // if object has no fields, it contains a child-message about that
      //return ((ObjectReference)value).referenceType().allFields().size() > 0;
    }

    return CompletableFuture.completedFuture(false);
  }

  @Override
  public boolean isApplicable(Type type) {
    return type instanceof ReferenceType && !(type instanceof ArrayType);
  }

  @Override
  public @NonNls String getName() {
    return "Object";
  }

  @Override
  public void setName(String text) {
    LOG.assertTrue(false);
  }

  @Nullable
  public static String getEnumConstantName(@NotNull ObjectReference objRef, ClassType classType) {
    do {
      if (!classType.isPrepared()) {
        return null;
      }
      classType = classType.superclass();
      if (classType == null) {
        return null;
      }
    }
    while (!(CommonClassNames.JAVA_LANG_ENUM.equals(classType.name())));
    final Field field = DebuggerUtils.findField(classType, "name");
    if (field == null) {
      return null;
    }
    final Value value = objRef.getValue(field);
    if (!(value instanceof StringReference)) {
      return null;
    }
    return ((StringReference)value).value();
  }
}
