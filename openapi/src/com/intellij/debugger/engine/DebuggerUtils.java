package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.CompletitionEditor;
import com.intellij.debugger.DebuggerContext;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.*;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.jdom.Element;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public abstract class DebuggerUtils  implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.DebuggerUtils");

  public static String getValueAsString(final EvaluationContext evaluationContext, Value value) throws EvaluateException {
    try {
      if (value == null) {
        return "null";
      }
      if (value instanceof StringReference) {
        return ((StringReference)value).value();
      }
      if (isInteger(value)) {
        long v = ((PrimitiveValue)value).longValue();
        return String.valueOf(v);
      }
      if (isNumeric(value)) {
        double v = ((PrimitiveValue)value).doubleValue();
        return String.valueOf(v);
      }
      if (value instanceof BooleanValue) {
        boolean v = ((PrimitiveValue)value).booleanValue();
        return String.valueOf(v);
      }
      if (value instanceof CharValue) {
        char v = ((PrimitiveValue)value).charValue();
        return String.valueOf(v);
      }
      if (value instanceof ObjectReference) {
        final ObjectReference objRef = (ObjectReference)value;
        ReferenceType refType = objRef.referenceType();
        if(refType instanceof ArrayType) {
          try {
            refType = (ReferenceType)objRef.virtualMachine().classesByName("java.lang.Object").get(0);
          }
          catch (Exception e) {
            throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate " + objRef.referenceType().name() + ".toString()");
          }
        }
        final Method toStringMethod = findMethod(refType, "toString", "()Ljava/lang/String;");
        if (toStringMethod == null) {
          throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate " + objRef.referenceType().name() + ".toString()");
        }

        StringReference stringReference = null;
        stringReference = (StringReference)evaluationContext.getDebugProcess().invokeMethod(
          evaluationContext, objRef,
          toStringMethod,
          Collections.EMPTY_LIST);

        return  stringReference == null ? "null" : stringReference.value();
      }
      throw EvaluateExceptionUtil.createEvaluateException("internal error : unsupported expression type");
    }
    catch (ObjectCollectedException e) {
      throw EvaluateExceptionUtil.OBJECT_WAS_COLLECTED;
    }
  }

  public static Method findMethod(ReferenceType refType, String methodName, String methodSignature) {
    Method method = null;
    if(methodSignature != null) {
      if (refType instanceof ClassType) {
        method = ((ClassType)refType).concreteMethodByName(methodName, methodSignature);
      }
      if (method == null) {
        List methods = refType.methodsByName(methodName, methodSignature);
        if (methods.size() > 0) {
          method = (Method)methods.get(0);
        }
      }
    } else {
      List methods = null;
      if (refType instanceof ClassType) {
        methods = refType.methodsByName(methodName);
      }
      if (methods != null && methods.size() == 0) {
        methods = refType.methodsByName(methodName);
      }
      if (methods != null && methods.size() > 0) {
        method = (Method)methods.get(0);
      }
    }

    return method;
  }

  public static boolean isNumeric(Value value) {
    return value != null &&
           (isInteger(value) ||
            value instanceof FloatValue ||
            value instanceof DoubleValue
           );
  }

  public static boolean isInteger(Value value) {
    return value != null &&
           (value instanceof ByteValue ||
            value instanceof ShortValue ||
            value instanceof LongValue ||
            value instanceof IntegerValue
           );
  }

  public static String translateStringValue(final String str) {
    int length = str.length();
    StringBuffer buffer = new StringBuffer(length + 16);
    StringUtil.escapeStringCharacters(length, str, buffer);
    if (str.length() > length) {
      buffer.append("...");
    }
    return buffer.toString();
  }

  protected static ArrayClass getArrayClass(String className) {
    boolean searchBracket = false;
    int dims = 0;
    int pos;

    for(pos = className.lastIndexOf(']'); pos >= 0; pos--){
      char c = className.charAt(pos);

      if (searchBracket) {
        if (c == '[') {
          dims++;
          searchBracket = false;
        }
        else if (!Character.isWhitespace(c)) break;
      }
      else {
        if (c == ']') {
          searchBracket = true;
        }
        else if (!Character.isWhitespace(c)) break;
      }
    }

    if (searchBracket) return null;

    if(dims == 0) return null;

    return new ArrayClass(className.substring(0, pos + 1), dims);
  }

  public static boolean instanceOf(String subType, String superType, Project project) {
    if(project == null) {
      return subType.equals(superType);
    }

    ArrayClass nodeClass = getArrayClass(subType);
    ArrayClass rendererClass = getArrayClass(superType);
    if (nodeClass == null || rendererClass == null) return false;

    if (nodeClass.dims == rendererClass.dims) {
      GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      PsiClass psiNodeClass = PsiManager.getInstance(project).findClass(nodeClass.className,
                                                                        scope);
      PsiClass psiRendererClass = PsiManager.getInstance(project).findClass(rendererClass.className,
                                                                            scope);
      return InheritanceUtil.isInheritorOrSelf(psiNodeClass, psiRendererClass, true);
    }
    else if (nodeClass.dims > rendererClass.dims) {
      return rendererClass.className.equals("java.lang.Object");
    }
    return false;
  }

  public static Type getSuperType(Type subType, String superType) {
    if("java.lang.Object".equals(superType)) {
      List list = subType.virtualMachine().classesByName("java.lang.Object");
      if(list.size() > 0) return (ReferenceType)list.get(0);
      return null;
    }

    return getSuperTypeInt(subType, superType);
  }

  private static Type getSuperTypeInt(Type subType, String superType) {
    Type result;
    if (subType == null) return null;

    if (subType.name().equals(superType)) return subType;

    if (subType instanceof ClassType) {
      result = getSuperType(((ClassType)subType).superclass(), superType);
      if (result != null) return result;

      List ifaces = ((ClassType)subType).allInterfaces();
      for (Iterator iterator = ifaces.iterator(); iterator.hasNext();) {
        InterfaceType interfaceType = (InterfaceType)iterator.next();
        if (interfaceType.name().equals(superType)) return interfaceType;
      }
      return null;
    }
    else if (subType instanceof InterfaceType) {
      List ifaces = ((InterfaceType)subType).superinterfaces();
      for (Iterator iterator = ifaces.iterator(); iterator.hasNext();) {
        InterfaceType interfaceType = (InterfaceType)iterator.next();
        result = getSuperType(interfaceType, superType);
        if (result != null) return result;
      }
    }
    else if (subType instanceof ArrayType) {
      if (superType.endsWith("[]")) {
        try {
          String superTypeItem = superType.substring(0, superType.length() - 2);
          Type subTypeItem = ((ArrayType)subType).componentType();
          return instanceOf(subTypeItem, superTypeItem) ? subType : null;
        }
        catch (ClassNotLoadedException e) {
          LOG.debug(e);
        }
      }
    }
    else if (subType instanceof PrimitiveType) {
      if(superType.equals("java.lang.Primitive")) {
        return subType;
      }
    }

    //only for interfaces and arrays
    if("java.lang.Object".equals(superType)) {
      List list = subType.virtualMachine().classesByName("java.lang.Object");
      if(list.size() > 0) return (ReferenceType)list.get(0);
    }
    return null;
  }

  public static boolean instanceOf(Type subType, String superType) {
    return getSuperType(subType, superType) != null;
  }

  public static PsiClass findClass(String className, Project project) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    
    ArrayClass arrayClass = getArrayClass(className);
    PsiManager psiManager = PsiManager.getInstance(project);
    if (arrayClass != null) {
      return psiManager.getElementFactory().getArrayClass();
    } else {
      if(project.isDefault()) {
        return null;
      }
      else {
        PsiClass psiClass = psiManager.findClass(className.replace('$', '.'), GlobalSearchScope.allScope(project));
        return psiClass;
      }
    }
  }

  public static void checkSyntax(PsiCodeFragment codeFragment) throws EvaluateException {
    PsiElement[] children = codeFragment.getChildren();

    if(children.length == 0) throw EvaluateExceptionUtil.createEvaluateException("Code fragment expected");
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];
      if(child instanceof PsiErrorElement) {
        throw EvaluateExceptionUtil.INVALID_EXPRESSION(child.getText());
      }
    }
  }

  protected static class ArrayClass {
    public String className;
    public int dims;

    public ArrayClass(String className, int dims) {
      this.className = className;
      this.dims = dims;
    }
  }

  public static DebuggerUtils getInstance() {
    return ApplicationManager.getApplication().getComponent(DebuggerUtils.class);
  }

  public abstract PsiExpression substituteThis(PsiExpression expressionWithThis, PsiExpression howToEvaluateThis, Value howToEvaluateThisValue, StackFrameContext context) throws EvaluateException;

  public abstract EvaluatorBuilder  getEvaluatorBuilder();
  public abstract DebuggerTreeNode  getSelectedNode    (DataContext context);
  public abstract DebuggerContext   getDebuggerContext (DataContext context);

  public abstract Element         writeTextWithImports(TextWithImports text);
  public abstract TextWithImports readTextWithImports (Element element);

  public abstract void            writeTextWithImports(Element root, String name, TextWithImports value);
  public abstract TextWithImports readTextWithImports (Element root, String name);

  public abstract TextWithImports    createExpressionText          (PsiExpression expression);
  public abstract TextWithImports    createExpressionWithImports   (String          expression);
  public abstract CompletitionEditor createEditor(Project project, PsiElement context, String recentsId);

  public abstract PsiElement getContextElement(final StackFrameContext context);

  public abstract PsiClass   chooseClassDialog(String title, Project project);
}
