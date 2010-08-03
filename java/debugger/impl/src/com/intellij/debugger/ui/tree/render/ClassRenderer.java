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
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import com.sun.jdi.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: lex
 * Date: Sep 17, 2003
 * Time: 2:04:00 PM
 */
public class ClassRenderer extends NodeRendererImpl{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.ClassRenderer");
  
  public static final @NonNls String UNIQUE_ID = "ClassRenderer";

  public boolean SORT_ASCENDING = false;
  public boolean SHOW_SYNTHETICS = true;
  public boolean SHOW_STATIC = false;
  public boolean SHOW_STATIC_FINAL = false;

  public boolean SHOW_DECLARED_TYPE = false;
  public boolean SHOW_OBJECT_ID = true;
  
  public ClassRenderer() {
    myProperties.setEnabled(true);
  }

  public String getUniqueId() {
    return UNIQUE_ID;
  }

  public boolean isEnabled() {
    return myProperties.isEnabled();
  }

  public void setEnabled(boolean enabled) {
    myProperties.setEnabled(enabled);
  }

  public ClassRenderer clone() {
    return (ClassRenderer) super.clone();
  }

  public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener)  throws EvaluateException {
    return calcLabel(descriptor);
  }

  protected static String calcLabel(ValueDescriptor descriptor) {
    final ValueDescriptorImpl valueDescriptor = (ValueDescriptorImpl)descriptor;
    final Value value = valueDescriptor.getValue();
    if (value instanceof ObjectReference) {
      final StringBuilder buf = StringBuilderSpinAllocator.alloc();
      try {
        if (value instanceof StringReference) {
          buf.append('\"');
          buf.append(((StringReference)value).value());
          buf.append('\"');
        }
        else if (value instanceof ClassObjectReference) {
          ReferenceType type = ((ClassObjectReference)value).reflectedType();
          buf.append((type != null)?type.name():"{...}");
        }
        else {
          final ObjectReference objRef = (ObjectReference)value;
          final Type type = objRef.type();
          if (type instanceof ClassType && ((ClassType)type).isEnum()) {
            final String name = getEnumConstantName(objRef, (ClassType)type);
            if (name != null) {
              buf.append(name);
            }
            else {
              buf.append(type.name());
            }
          }
          else {
            buf.append(ValueDescriptorImpl.getIdLabel(objRef));
          }
        }
        return buf.toString();
      }
      finally {
        StringBuilderSpinAllocator.dispose(buf);
      }
    }
    else if(value == null) {
      //noinspection HardCodedStringLiteral
      return "null";
    }
    else {
      return DebuggerBundle.message("label.undefined");
    }
  }

  public void buildChildren(final Value value, final ChildrenBuilder builder, final EvaluationContext evaluationContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final ValueDescriptorImpl parentDescriptor = (ValueDescriptorImpl)builder.getParentDescriptor();
    final NodeManager nodeManager = builder.getNodeManager();
    final NodeDescriptorFactory nodeDescriptorFactory = builder.getDescriptorManager();

    List<DebuggerTreeNode> children = new ArrayList<DebuggerTreeNode>();
    if (value instanceof ObjectReference) {
      final ObjectReference objRef = (ObjectReference)value;
      final ReferenceType refType = objRef.referenceType();
      // default ObjectReference processing
      final List<Field> fields = refType.allFields();
      if (fields.size() > 0) {
        for (final Field field : fields) {
          if (!shouldDisplay(evaluationContext, objRef, field)) {
            continue;
          }
          children.add(nodeManager.createNode(nodeDescriptorFactory.getFieldDescriptor(parentDescriptor, objRef, field), evaluationContext));
        }

        if(SORT_ASCENDING) {
          Collections.sort(children, NodeManagerImpl.getNodeComparator());
        }
      }
      else {
        children.add(nodeManager.createMessageNode(MessageDescriptor.CLASS_HAS_NO_FIELDS.getLabel()));
      }
    }
    builder.setChildren(children);
  }

  private boolean shouldDisplay(EvaluationContext context, @NotNull ObjectReference objInstance, @NotNull Field field) {
    final boolean isSynthetic = DebuggerUtils.isSynthetic(field);
    if (!SHOW_SYNTHETICS && isSynthetic) {
      return false;
    }
    if (isSynthetic) {
      if (objInstance.equals(context.getThisObject()) && StringUtil.startsWith(field.name(), FieldDescriptorImpl.OUTER_LOCAL_VAR_FIELD_PREFIX)) {
        return false;
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

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public PsiExpression getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    FieldDescriptor fieldDescriptor = (FieldDescriptor)node.getDescriptor();

    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(node.getProject()).getElementFactory();
    try {
      return elementFactory.createExpressionFromText(fieldDescriptor.getField().name(), DebuggerUtils.findClass(
        fieldDescriptor.getObject().referenceType().name(), context.getProject(), context.getDebugProcess().getSearchScope())
      );
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(DebuggerBundle.message("error.invalid.field.name", fieldDescriptor.getField().name()), null);
    }
  }

  private static boolean valueExpandable(Value value)  {
    try {
      if(value instanceof ArrayReference) {
        return ((ArrayReference)value).length() > 0;
      }
      else if(value instanceof ObjectReference) {
        return ((ObjectReference)value).referenceType().allFields().size() > 0;
      }
    }
    catch (ObjectCollectedException e) {
      return true;
    }

    return false;
  }

  public boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return valueExpandable(value);
  }

  public boolean isApplicable(Type type) {
    return type instanceof ReferenceType && !(type instanceof ArrayType);
  }

  public @NonNls String getName() {
    return "Object";
  }

  public void setName(String text) {
    LOG.assertTrue(false);
  }

  @Nullable
  public static String getEnumConstantName(final ObjectReference objRef, ClassType classType) {
    do {
      if (!classType.isPrepared()) {
        return null;
      }
      classType = classType.superclass();
      if (classType == null) {
        return null;
      }
    }
    while (!("java.lang.Enum".equals(classType.name())));
    //noinspection HardCodedStringLiteral
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
