/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerContextUtil;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.watch.ArgumentValueDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.MethodReturnValueDescriptorImpl;
import com.intellij.debugger.ui.tree.FieldDescriptor;
import com.intellij.debugger.ui.tree.LocalVariableDescriptor;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ClassNotPreparedException;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class DefaultSourcePositionProvider extends SourcePositionProvider {
  @Nullable
  @Override
  protected SourcePosition computeSourcePosition(@NotNull NodeDescriptor descriptor,
                                                 @NotNull Project project,
                                                 @NotNull DebuggerContextImpl context,
                                                 boolean nearest) {
    StackFrameProxyImpl frame = context.getFrameProxy();
    if (frame == null) {
      return null;
    }

    if (descriptor instanceof FieldDescriptor) {
      return getSourcePositionForField((FieldDescriptor)descriptor, project, context, nearest);
    }
    else if (descriptor instanceof LocalVariableDescriptor) {
      return getSourcePositionForLocalVariable(descriptor.getName(), project, context, nearest);
    }
    else if (descriptor instanceof ArgumentValueDescriptorImpl) {
      Collection<String> names = ((ArgumentValueDescriptorImpl)descriptor).getVariable().getMatchedNames();
      if (!names.isEmpty()) {
        return getSourcePositionForLocalVariable(names.iterator().next(), project, context, nearest);
      }
    }
    else if (descriptor instanceof MethodReturnValueDescriptorImpl) {
      DebugProcessImpl debugProcess = context.getDebugProcess();
      if (debugProcess != null) {
        return debugProcess.getPositionManager().getSourcePosition(((MethodReturnValueDescriptorImpl)descriptor).getMethod().location());
      }
    }
    return null;
  }

  @Nullable
  private static SourcePosition getSourcePositionForField(@NotNull FieldDescriptor descriptor,
                                                          @NotNull Project project,
                                                          @NotNull DebuggerContextImpl context,
                                                          boolean nearest) {
    final ReferenceType type = descriptor.getField().declaringType();
    final String fieldName = descriptor.getField().name();
    if (fieldName.startsWith(FieldDescriptorImpl.OUTER_LOCAL_VAR_FIELD_PREFIX)) {
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
      PsiElement navigationElement = aClass.getNavigationElement();
      if (!(navigationElement instanceof PsiClass)) {
        return null;
      }
      aClass = (PsiClass)navigationElement;
      PsiVariable psiVariable = JavaPsiFacade.getInstance(project).getResolveHelper().resolveReferencedVariable(varName, aClass);
      if (psiVariable == null) {
        return null;
      }
      if (nearest) {
        return DebuggerContextUtil.findNearest(context, psiVariable, aClass.getContainingFile());
      }
      return SourcePosition.createFromElement(psiVariable);
    }
    else {
      PsiClass aClass = null;
      DebugProcessImpl debugProcess = context.getDebugProcess();
      if (debugProcess != null) {
        try {
          List<Location> locations = type.allLineLocations();
          if (!locations.isEmpty()) {
            // important: use the last location to be sure the position will be within the anonymous class
            aClass = JVMNameUtil.getClassAt(debugProcess.getPositionManager().getSourcePosition(ContainerUtil.getLastItem(locations)));
          }
        }
        catch (AbsentInformationException | ClassNotPreparedException ignored) {
        }
      }

      if (aClass == null) { // fallback
        DebuggerSession session = context.getDebuggerSession();
        GlobalSearchScope scope = session != null ? session.getSearchScope() : GlobalSearchScope.allScope(project);
        aClass = DebuggerUtils.findClass(type.name(), project, scope);
      }

      if (aClass != null) {
        PsiField field = aClass.findFieldByName(fieldName, false);
        if (field == null) return null;
        if (nearest) {
          return DebuggerContextUtil.findNearest(context, field.getNavigationElement(), aClass.getContainingFile());
        }
        return SourcePosition.createFromElement(field);
      }
      return null;
    }
  }

  @Nullable
  private static SourcePosition getSourcePositionForLocalVariable(String name,
                                                                  @NotNull Project project,
                                                                  @NotNull DebuggerContextImpl context,
                                                                  boolean nearest) {
    PsiElement place = PositionUtil.getContextElement(context);
    if (place == null) return null;

    PsiVariable psiVariable = JavaPsiFacade.getInstance(project).getResolveHelper().resolveReferencedVariable(name, place);
    if (psiVariable == null) return null;

    PsiFile containingFile = psiVariable.getContainingFile();
    if(containingFile == null) return null;
    try {
      if (nearest) {
        return DebuggerContextUtil.findNearest(context, psiVariable, containingFile);
      }
    }
    catch (IndexNotReadyException ignore) {
    }
    return SourcePosition.createFromElement(psiVariable);
  }
}