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
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import com.sun.jdi.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class DebuggerUtils  implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.DebuggerUtils");
  private static final Key<Method> TO_STRING_METHOD_KEY = new Key<Method>("CachedToStringMethod");
  public static final Set<String> ourPrimitiveTypeNames = new HashSet<String>(Arrays.asList(
      "byte", "short", "int", "long", "float", "double", "boolean", "char"
  ));

  @NonNls
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
        if (value instanceof ArrayReference) {
          final StringBuilder builder = new StringBuilder();
          builder.append("[");
          for (Iterator<Value> iterator = ((ArrayReference)value).getValues().iterator(); iterator.hasNext();) {
            final Value element = iterator.next();
            builder.append(getValueAsString(evaluationContext, element));
            if (iterator.hasNext()) {
              builder.append(",");
            }
          }
          builder.append("]");
          return builder.toString();
        }

        final ObjectReference objRef = (ObjectReference)value;
        final DebugProcess debugProcess = evaluationContext.getDebugProcess();
        Method toStringMethod = debugProcess.getUserData(TO_STRING_METHOD_KEY);
        if (toStringMethod == null) {
          try {
            ReferenceType refType = objRef.virtualMachine().classesByName("java.lang.Object").get(0);
            toStringMethod = findMethod(refType, "toString", "()Ljava/lang/String;");
            debugProcess.putUserData(TO_STRING_METHOD_KEY, toStringMethod);
          }
          catch (Exception e) {
            throw EvaluateExceptionUtil.createEvaluateException(
              DebuggerBundle.message("evaluation.error.cannot.evaluate.tostring", objRef.referenceType().name()));
          }
        }
        if (toStringMethod == null) {
          throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.cannot.evaluate.tostring", objRef.referenceType().name()));
        }
        final StringReference stringReference = (StringReference)debugProcess.invokeInstanceMethod(evaluationContext, objRef, toStringMethod, Collections.emptyList(), 0);
        return  stringReference == null ? "null" : stringReference.value();
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.unsupported.expression.type"));
    }
    catch (ObjectCollectedException e) {
      throw EvaluateExceptionUtil.OBJECT_WAS_COLLECTED;
    }
  }

  @Nullable
  public static Method findMethod(ReferenceType refType, @NonNls String methodName, @NonNls String methodSignature) {
    if (refType instanceof ArrayType) {
      // for array types methodByName() in JDI always returns empty list
      final Method method = findMethod(refType.virtualMachine().classesByName("java.lang.Object").get(0), methodName, methodSignature);
      if (method != null) {
        return method;
      }
    }

    Method method = null;
    if (methodSignature != null) {
      if (refType instanceof ClassType) {
        method = ((ClassType)refType).concreteMethodByName(methodName, methodSignature);
      }
      if (method == null) {
        final List<Method> methods = refType.methodsByName(methodName, methodSignature);
        if (methods.size() > 0) {
          method = methods.get(0);
        }
      }
    }
    else {
      List<Method> methods = null;
      if (refType instanceof ClassType) {
        methods = refType.methodsByName(methodName);
      }
      if (methods != null && methods.size() > 0) {
        method = methods.get(0);
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
    final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      StringUtil.escapeStringCharacters(length, str, buffer);
      if (str.length() > length) {
        buffer.append("...");
      }
      return buffer.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
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
      PsiClass psiNodeClass = JavaPsiFacade.getInstance(project).findClass(nodeClass.className, scope);
      PsiClass psiRendererClass = JavaPsiFacade.getInstance(project).findClass(rendererClass.className, scope);
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
      if(list.size() > 0) {
        return (ReferenceType)list.get(0);
      }
      return null;
    }

    return getSuperTypeInt(subType, superType);
  }

  private static Type getSuperTypeInt(Type subType, String superType) {
    Type result;
    if (subType == null) {
      return null;
    }

    if (subType.name().equals(superType)) {
      return subType;
    }

    if (subType instanceof ClassType) {
      result = getSuperType(((ClassType)subType).superclass(), superType);
      if (result != null) {
        return result;
      }

      List ifaces = ((ClassType)subType).allInterfaces();
      for (Iterator iterator = ifaces.iterator(); iterator.hasNext();) {
        InterfaceType interfaceType = (InterfaceType)iterator.next();
        if (interfaceType.name().equals(superType)) {
          return interfaceType;
        }
      }
      return null;
    }

    if (subType instanceof InterfaceType) {
      List ifaces = ((InterfaceType)subType).superinterfaces();
      for (Iterator iterator = ifaces.iterator(); iterator.hasNext();) {
        InterfaceType interfaceType = (InterfaceType)iterator.next();
        result = getSuperType(interfaceType, superType);
        if (result != null) {
          return result;
        }
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
      //noinspection HardCodedStringLiteral
      if(superType.equals("java.lang.Primitive")) {
        return subType;
      }
    }

    //only for interfaces and arrays
    if("java.lang.Object".equals(superType)) {
      List list = subType.virtualMachine().classesByName("java.lang.Object");
      if(list.size() > 0) {
        return (ReferenceType)list.get(0);
      }
    }
    return null;
  }

  public static boolean instanceOf(Type subType, String superType) {
    return getSuperType(subType, superType) != null;
  }

  public static PsiClass findClass(String className, Project project, final GlobalSearchScope scope) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    final PsiManager psiManager = PsiManager.getInstance(project);
    if (getArrayClass(className) != null) {
      return JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().getArrayClass(LanguageLevelProjectExtension.getInstance(psiManager.getProject()).getLanguageLevel());
    }
    if(project.isDefault()) {
      return null;
    }
    final String _className = className.replace('$', '.');
    final PsiClass aClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(_className, scope);
    if (aClass == null && scope != GlobalSearchScope.allScope(project)) {
      return JavaPsiFacade.getInstance(psiManager.getProject()).findClass(_className, GlobalSearchScope.allScope(project));
    }
    return aClass;
  }

  public static PsiType getType(String className, Project project) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final PsiManager psiManager = PsiManager.getInstance(project);
    try {
      if (getArrayClass(className) != null) {
        return JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createTypeFromText(className, null);
      }
      if(project.isDefault()) {
        return null;
      }
      final PsiClass aClass =
        JavaPsiFacade.getInstance(psiManager.getProject()).findClass(className.replace('$', '.'), GlobalSearchScope.allScope(project));
      return JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createType(aClass);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return null;
  }

  public static void checkSyntax(PsiCodeFragment codeFragment) throws EvaluateException {
    PsiElement[] children = codeFragment.getChildren();

    if(children.length == 0) throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.empty.code.fragment"));
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];
      if(child instanceof PsiErrorElement) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", child.getText()));
      }
    }
  }

  public static boolean hasSideEffects(PsiElement element) {
    return hasSideEffectsOrReferencesMissingVars(element, null);
  }
  
  public static boolean hasSideEffectsOrReferencesMissingVars(PsiElement element, @Nullable final Set<String> visibleLocalVariables) {
    final Ref<Boolean> rv = new Ref<Boolean>(Boolean.FALSE);
    element.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override 
      public void visitPostfixExpression(final PsiPostfixExpression expression) {
        rv.set(Boolean.TRUE);
      }

      @Override 
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
        final PsiElement psiElement = expression.resolve();
        if (psiElement instanceof PsiLocalVariable) {
          if (visibleLocalVariables != null) {
            if (!visibleLocalVariables.contains(((PsiLocalVariable)psiElement).getName())) {
              rv.set(Boolean.TRUE);
            }
          }
        }
        else if (psiElement instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)psiElement;
          if (!isSimpleGetter(method)) {
            rv.set(Boolean.TRUE);
          }
        }
        if (!rv.get().booleanValue()) {
          super.visitReferenceExpression(expression);
        }
      }

      @Override 
      public void visitPrefixExpression(final PsiPrefixExpression expression) {
        final IElementType op = expression.getOperationTokenType();
        if (JavaTokenType.PLUSPLUS.equals(op) || JavaTokenType.MINUSMINUS.equals(op)) {
          rv.set(Boolean.TRUE);
        }
        else {
          super.visitPrefixExpression(expression);
        }
      }

      @Override 
      public void visitAssignmentExpression(final PsiAssignmentExpression expression) {
        rv.set(Boolean.TRUE);
      }

      @Override 
      public void visitCallExpression(final PsiCallExpression callExpression) {
        final PsiMethod method = callExpression.resolveMethod();
        if (method == null || !isSimpleGetter(method)) {
          rv.set(Boolean.TRUE);
        }
        else {
          super.visitCallExpression(callExpression);
        }
      }
    });
    return rv.get().booleanValue();
  }

  public abstract String findAvailableDebugAddress(boolean useSockets) throws ExecutionException;

  public static boolean isSynthetic(TypeComponent typeComponent) {
    if (typeComponent == null) {
      return false;
    }
    VirtualMachine machine = typeComponent.virtualMachine();
    return machine != null && machine.canGetSyntheticAttribute() && typeComponent.isSynthetic();
  }

  public static boolean isSimpleGetter(PsiMethod method){
    final PsiCodeBlock body = method.getBody();
    if(body == null){
      return false;
    }

    final PsiStatement[] statements = body.getStatements();
    if(statements.length != 1){
      return false;
    }
    
    final PsiStatement statement = statements[0];
    if(!(statement instanceof PsiReturnStatement)){
      return false;
    }
    
    final PsiExpression value = ((PsiReturnStatement)statement).getReturnValue();
    if(!(value instanceof PsiReferenceExpression)){
      return false;
    }
    
    final PsiReferenceExpression reference = (PsiReferenceExpression)value;
    final PsiExpression qualifier = reference.getQualifierExpression();
    //noinspection HardCodedStringLiteral
    if(qualifier != null && !"this".equals(qualifier.getText())) {
      return false;
    }
    
    final PsiElement referent = reference.resolve();
    if(referent == null) {
      return false;
    }
    
    if(!(referent instanceof PsiField)) {
      return false;
    }
    
    return ((PsiField)referent).getContainingClass().equals(method.getContainingClass());
  }

  public static boolean isPrimitiveType(final String typeName) {
    return ourPrimitiveTypeNames.contains(typeName);
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
    return ServiceManager.getService(DebuggerUtils.class);
  }

  public abstract PsiExpression substituteThis(PsiExpression expressionWithThis, PsiExpression howToEvaluateThis, Value howToEvaluateThisValue, StackFrameContext context) throws EvaluateException;

  public abstract DebuggerContext getDebuggerContext (DataContext context);

  public abstract Element writeTextWithImports(TextWithImports text);
  public abstract TextWithImports readTextWithImports (Element element);

  public abstract void writeTextWithImports(Element root, @NonNls String name, TextWithImports value);
  public abstract TextWithImports readTextWithImports (Element root, @NonNls String name);

  public abstract TextWithImports createExpressionWithImports(@NonNls String expression);

  public abstract PsiElement getContextElement(final StackFrameContext context);

  public abstract PsiClass chooseClassDialog(String title, Project project);

  public static boolean supportsJVMDebugging(FileType type) {
    return type instanceof LanguageFileType && ((LanguageFileType)type).isJVMDebuggingSupported();
  }

  public static boolean supportsJVMDebugging(PsiFile file) {
    final JVMDebugProvider[] providers = Extensions.getExtensions(JVMDebugProvider.EP_NAME);
    for (JVMDebugProvider provider : providers) {
      if (provider.supportsJVMDebugging(file)) {
        return true;
      }
    }
    return false;
  }
}
