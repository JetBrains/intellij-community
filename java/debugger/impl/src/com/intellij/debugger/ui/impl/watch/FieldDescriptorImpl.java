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

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.tree.FieldDescriptor;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.render.ClassRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FieldDescriptorImpl extends ValueDescriptorImpl implements FieldDescriptor{
  public static final String OUTER_LOCAL_VAR_FIELD_PREFIX = "val$";
  private final Field myField;
  private final ObjectReference myObject;
  private Boolean myIsPrimitive = null;
  private final boolean myIsStatic;

  public FieldDescriptorImpl(Project project, ObjectReference objRef, @NotNull Field field) {
    super(project);
    myObject = objRef;
    myField = field;
    myIsStatic = field.isStatic();
    setLvalue(!field.isFinal());
  }

  @Override
  public Field getField() {
    return myField;
  }

  @Override
  public ObjectReference getObject() {
    return myObject;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public SourcePosition getSourcePosition(final Project project, final DebuggerContextImpl context) {
    if (context.getFrameProxy() == null) {
      return null;
    }
    final ReferenceType type = myField.declaringType();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final String fieldName = myField.name();
    if (fieldName.startsWith(OUTER_LOCAL_VAR_FIELD_PREFIX)) {
      // this field actually mirrors a local variable in the outer class
      String varName = fieldName.substring(fieldName.lastIndexOf('$') + 1);
      PsiElement element = PositionUtil.getContextElement(context);
      if (element == null) {
        return null;
      }
      PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
      if (aClass == null) {
        return null;
      }
      aClass = (PsiClass) aClass.getNavigationElement();
      PsiVariable psiVariable = facade.getResolveHelper().resolveReferencedVariable(varName, aClass);
      if (psiVariable == null) {
        return null;
      }
      return SourcePosition.createFromOffset(psiVariable.getContainingFile(), psiVariable.getTextOffset());
    }
    else {
      final DebuggerSession session = context.getDebuggerSession();
      final GlobalSearchScope scope = session != null? session.getSearchScope() : GlobalSearchScope.allScope(myProject);
      PsiClass aClass = facade.findClass(type.name().replace('$', '.'), scope);
      if (aClass == null) {
        // trying to search, assuming declaring class is an anonymous class
        final DebugProcessImpl debugProcess = context.getDebugProcess();
        if (debugProcess != null) {
          try {
            final List<Location> locations = type.allLineLocations();
            if (!locations.isEmpty()) {
              // important: use the last location to be sure the position will be within the anonymous class
              final Location lastLocation = locations.get(locations.size() - 1);
              final SourcePosition position = debugProcess.getPositionManager().getSourcePosition(lastLocation);
              if (position != null) {
                aClass = JVMNameUtil.getClassAt(position);
              }
            }
          }
          catch (AbsentInformationException ignored) {
          }
          catch (ClassNotPreparedException ignored) {
          }
        }
      }

      if (aClass != null) {
        aClass = (PsiClass) aClass.getNavigationElement();
        for (PsiField field : aClass.getFields()) {
          if (fieldName.equals(field.getName())) {
            return SourcePosition.createFromOffset(field.getContainingFile(), field.getTextOffset());
          }
        }
      }
      return null;
    }
  }

  @Override
  public void setAncestor(NodeDescriptor oldDescriptor) {
    super.setAncestor(oldDescriptor);
    final Boolean isPrimitive = ((FieldDescriptorImpl)oldDescriptor).myIsPrimitive;
    if (isPrimitive != null) { // was cached
      // do not loose cached info
      myIsPrimitive = isPrimitive;
    }
  }


  @Override
  public boolean isPrimitive() {
    if (myIsPrimitive == null) {
      final Value value = getValue();
      if (value != null) {
        myIsPrimitive = super.isPrimitive();
      }
      else {
        myIsPrimitive = DebuggerUtils.isPrimitiveType(myField.typeName()) ? Boolean.TRUE : Boolean.FALSE;
      }
    }
    return myIsPrimitive.booleanValue();
  }

  @Override
  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      return (myObject != null) ? myObject.getValue(myField) : myField.declaringType().getValue(myField);
    }
    catch (ObjectCollectedException ignored) {
      throw EvaluateExceptionUtil.OBJECT_WAS_COLLECTED;
    }
  }

  public boolean isStatic() {
    return myIsStatic;
  }

  @Override
  public String getName() {
    final String fieldName = myField.name();
    if (isOuterLocalVariableValue() && NodeRendererSettings.getInstance().getClassRenderer().SHOW_VAL_FIELDS_AS_LOCAL_VARIABLES) {
      return StringUtil.trimStart(fieldName, OUTER_LOCAL_VAR_FIELD_PREFIX);
    }
    return fieldName;
  }

  public boolean isOuterLocalVariableValue() {
    try {
      return DebuggerUtils.isSynthetic(myField) && myField.name().startsWith(OUTER_LOCAL_VAR_FIELD_PREFIX);
    }
    catch (UnsupportedOperationException ignored) {
      return false;
    }
  }

  @Override
  public String calcValueName() {
    final ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
    StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      buf.append(getName());
      if (classRenderer.SHOW_DECLARED_TYPE) {
        buf.append(": ");
        buf.append(classRenderer.renderTypeName(myField.typeName()));
      }
      return buf.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }
  }

  @Override
  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
    String fieldName;
    if(isStatic()) {
      String typeName = myField.declaringType().name().replace('$', '.');
      typeName = DebuggerTreeNodeExpression.normalize(typeName, PositionUtil.getContextElement(context), context.getProject());
      fieldName = typeName + "." + getName();
    }
    else {
      //noinspection HardCodedStringLiteral
      fieldName = isOuterLocalVariableValue()? StringUtil.trimStart(getName(), OUTER_LOCAL_VAR_FIELD_PREFIX) : "this." + getName();
    }
    try {
      return elementFactory.createExpressionFromText(fieldName, null);
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(DebuggerBundle.message("error.invalid.field.name", getName()), e);
    }
  }
}
