// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
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
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import com.sun.jdi.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ClassRenderer extends NodeRendererImpl{
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
    return (ClassRenderer) super.clone();
  }

  @Override
  public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener)
    throws EvaluateException {
    return calcLabel(descriptor, evaluationContext);
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

    List<DebuggerTreeNode> children = new ArrayList<>();
    if (value instanceof ObjectReference) {
      final ObjectReference objRef = (ObjectReference)value;
      final ReferenceType refType = objRef.referenceType();
      // default ObjectReference processing
      List<Field> fields = refType.allFields();
      if (!fields.isEmpty()) {
        Set<String> names = new HashSet<>();
        List<Field> fieldsToShow = ContainerUtil.filter(fields, field -> shouldDisplay(evaluationContext, objRef, field));
        int loaded = 0, total = fieldsToShow.size();
        Map<Field, Value> cachedValues = null;
        for (int i = 0; i < total; i++) {
          Field field = fieldsToShow.get(i);
          // load values in chunks
          if (i > loaded || cachedValues == null) {
            int chunkSize = Math.min(XCompositeNode.MAX_CHILDREN_TO_SHOW, total - loaded);
            try {
              cachedValues = objRef.getValues(fieldsToShow.subList(loaded, loaded + chunkSize));
            } catch (Exception e) {
              LOG.error(e);
              cachedValues = null;
            }
            loaded += chunkSize;
          }

          FieldDescriptorImpl fieldDescriptor =
            (FieldDescriptorImpl)createFieldDescriptor(parentDescriptor, nodeDescriptorFactory, objRef, field, evaluationContext);
          if (cachedValues != null) {
            fieldDescriptor.setValue(cachedValues.get(field));
          }
          String name = fieldDescriptor.getName();
          if (names.contains(name)) {
            fieldDescriptor.putUserData(FieldDescriptor.SHOW_DECLARING_TYPE, Boolean.TRUE);
          }
          else {
            names.add(name);
          }
          children.add(nodeManager.createNode(fieldDescriptor, evaluationContext));
        }

        if (children.isEmpty()) {
          children.add(nodeManager.createMessageNode(JavaDebuggerBundle.message("message.node.class.no.fields.to.display")));
        }
        else if (XDebuggerSettingsManager.getInstance().getDataViewSettings().isSortValues()) {
          children.sort(NodeManagerImpl.getNodeComparator());
        }
      }
      else {
        children.add(nodeManager.createMessageNode(MessageDescriptor.CLASS_HAS_NO_FIELDS.getLabel()));
      }
    }
    builder.setChildren(children);
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
    if(!SHOW_STATIC && field.isStatic()) {
      return false;
    }

    if(!SHOW_STATIC_FINAL && field.isStatic() && field.isFinal()) {
      return false;
    }

    return true;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    DefaultJDOMExternalizer.writeExternal(this, element, new DifferenceFilter<>(this, new ClassRenderer()));
  }

  @Override
  public PsiElement getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    FieldDescriptor fieldDescriptor = (FieldDescriptor)node.getDescriptor();

    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(node.getProject());
    try {
      return elementFactory.createExpressionFromText("this." + fieldDescriptor.getField().name(), DebuggerUtils.findClass(
        fieldDescriptor.getObject().referenceType().name(), context.getProject(), context.getDebugProcess().getSearchScope())
      );
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(JavaDebuggerBundle.message("error.invalid.field.name", fieldDescriptor.getField().name()), null);
    }
  }

  private static boolean valueExpandable(Value value)  {
    try {
      if(value instanceof ArrayReference) {
        return ((ArrayReference)value).length() > 0;
      }
      else if(value instanceof ObjectReference) {
        return true; // if object has no fields, it contains a child-message about that
        //return ((ObjectReference)value).referenceType().allFields().size() > 0;
      }
    }
    catch (ObjectCollectedException e) {
      return true;
    }

    return false;
  }

  @Override
  public boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return valueExpandable(value);
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
    final Field field = classType.fieldByName("name");
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
