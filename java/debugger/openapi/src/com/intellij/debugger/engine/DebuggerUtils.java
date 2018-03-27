/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class DebuggerUtils {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.DebuggerUtils");
  private static final Key<Method> TO_STRING_METHOD_KEY = new Key<>("CachedToStringMethod");
  public static final Set<String> ourPrimitiveTypeNames = new HashSet<>(Arrays.asList(
    "byte", "short", "int", "long", "float", "double", "boolean", "char"
  ));

  public static void cleanupAfterProcessFinish(DebugProcess debugProcess) {
    debugProcess.putUserData(TO_STRING_METHOD_KEY, null);
  }

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
        return String.valueOf(((PrimitiveValue)value).longValue());
      }
      if (value instanceof FloatValue) {
        return String.valueOf(((FloatValue)value).floatValue());
      }
      if (value instanceof DoubleValue) {
        return String.valueOf(((DoubleValue)value).doubleValue());
      }
      if (value instanceof BooleanValue) {
        return String.valueOf(((PrimitiveValue)value).booleanValue());
      }
      if (value instanceof CharValue) {
        return String.valueOf(((PrimitiveValue)value).charValue());
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
            ReferenceType refType = getObjectClassType(objRef.virtualMachine());
            toStringMethod = findMethod(refType, "toString", "()Ljava/lang/String;");
            debugProcess.putUserData(TO_STRING_METHOD_KEY, toStringMethod);
          }
          catch (Exception ignored) {
            throw EvaluateExceptionUtil.createEvaluateException(
              DebuggerBundle.message("evaluation.error.cannot.evaluate.tostring", objRef.referenceType().name()));
          }
        }
        if (toStringMethod == null) {
          throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.cannot.evaluate.tostring", objRef.referenceType().name()));
        }
        // while result must be of com.sun.jdi.StringReference type, it turns out that sometimes (jvm bugs?)
        // it is a plain com.sun.tools.jdi.ObjectReferenceImpl
        final Value result = debugProcess.invokeInstanceMethod(evaluationContext, objRef, toStringMethod, Collections.emptyList(), 0);
        if (result == null) {
          return "null";
        }
        return result instanceof StringReference ? ((StringReference)result).value() : result.toString();
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.unsupported.expression.type"));
    }
    catch (ObjectCollectedException ignored) {
      throw EvaluateExceptionUtil.OBJECT_WAS_COLLECTED;
    }
  }

  public static final int MAX_DISPLAY_LABEL_LENGTH = 1024 * 5;

  public static String convertToPresentationString(String str) {
    if (str.length() > MAX_DISPLAY_LABEL_LENGTH) {
      str = translateStringValue(str.substring(0, MAX_DISPLAY_LABEL_LENGTH));
      StringBuilder buf = new StringBuilder();
      buf.append(str);
      if (!str.endsWith("...")) {
        buf.append("...");
      }
      return buf.toString();
    }
    return translateStringValue(str);
  }

  @Nullable
  public static Method findMethod(@NotNull ReferenceType refType, @NonNls String methodName, @Nullable @NonNls String methodSignature) {
    if (refType instanceof ArrayType) {
      // for array types methodByName() in JDI always returns empty list
      Method method = findMethod(getObjectClassType(refType.virtualMachine()), methodName, methodSignature);
      if (method != null) {
        return method;
      }
      // for arrays, clone signature may return array of objects, there is no such method in Object class
      if ("clone".equals(methodName) && "()[Ljava/lang/Object;".equals(methodSignature)) {
        method = findMethod(getObjectClassType(refType.virtualMachine()), "clone", null);
        if (method != null) {
          return method;
        }
      }
    }

    Method method = null;
    if (methodSignature != null) {
      if (refType instanceof ClassType) {
        method = ((ClassType)refType).concreteMethodByName(methodName, methodSignature);
      }
      if (method == null) {
        method = ContainerUtil.getFirstItem(refType.methodsByName(methodName, methodSignature));
      }
    }
    else {
      method = ContainerUtil.getFirstItem(refType.methodsByName(methodName));
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
    return (value instanceof ByteValue ||
            value instanceof ShortValue ||
            value instanceof LongValue ||
            value instanceof IntegerValue
    );
  }

  public static String translateStringValue(final String str) {
    int length = str.length();
    final StringBuilder buffer = new StringBuilder();
    StringUtil.escapeStringCharacters(length, str, buffer);
    return buffer.toString();
  }

  @Nullable
  protected static ArrayClass getArrayClass(@NotNull String className) {
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

  public static boolean instanceOf(@NotNull String subType ,@NotNull String superType, @Nullable Project project) {
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
      return rendererClass.className.equals(CommonClassNames.JAVA_LANG_OBJECT);
    }
    return false;
  }

  public static boolean instanceOf(@Nullable Type subType, @NotNull String superType) {
    if (subType == null || subType instanceof VoidType) {
      return false;
    }

    if (subType instanceof PrimitiveType) {
      return superType.equals(subType.name());
    }

    if (CommonClassNames.JAVA_LANG_OBJECT.equals(superType)) {
      return true;
    }

    return getSuperTypeInt(subType, superType) != null;
  }

  @Nullable
  public static Type getSuperType(@Nullable Type subType, @NotNull String superType) {
    if (subType == null || subType instanceof PrimitiveType || subType instanceof VoidType) {
      return null;
    }

    if (CommonClassNames.JAVA_LANG_OBJECT.equals(superType)) {
      return getObjectClassType(subType.virtualMachine());
    }

    return getSuperTypeInt(subType, superType);
  }

  private static ReferenceType getObjectClassType(VirtualMachine virtualMachine) {
    return ContainerUtil.getFirstItem(virtualMachine.classesByName(CommonClassNames.JAVA_LANG_OBJECT));
  }

  private static boolean typeEquals(@NotNull Type type, @NotNull String typeName) {
    int genericPos = typeName.indexOf('<');
    if (genericPos > -1) {
      typeName = typeName.substring(0, genericPos);
    }
    return type.name().replace('$', '.').equals(typeName.replace('$', '.'));
  }

  private static Type getSuperTypeInt(@NotNull Type subType, @NotNull String superType) {
    if (typeEquals(subType, superType)) {
      return subType;
    }

    Type result;
    if (subType instanceof ClassType) {
      try {
        ClassType clsType = (ClassType)subType;
        result = getSuperType(clsType.superclass(), superType);
        if (result != null) {
          return result;
        }

        for (InterfaceType iface : clsType.allInterfaces()) {
          if (typeEquals(iface, superType)) {
            return iface;
          }
        }
      }
      catch (ClassNotPreparedException e) {
        LOG.info(e);
      }
      return null;
    }

    if (subType instanceof InterfaceType) {
      try {
        for (InterfaceType iface : ((InterfaceType)subType).superinterfaces()) {
          result = getSuperType(iface, superType);
          if (result != null) {
            return result;
          }
        }
      }
      catch (ClassNotPreparedException e) {
        LOG.info(e);
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
          LOG.info(e);
        }
      }
    }

    return null;
  }

  // compilable version of array class for compiling evaluator
  private static final String ARRAY_CLASS_NAME = "__Dummy_Array__";
  private static final String ARRAY_CLASS_TEXT =
    "public class " + ARRAY_CLASS_NAME + "<T> {" +
    "  public final int length;" +
    "  private " + ARRAY_CLASS_NAME + "(int l) {length = l;}" +
    "  public T[] clone() {return null;}" +
    "}";

  // workaround to get an array class of needed language version for correct HL in array renderers expression
  private static PsiClass createArrayClass(Project project, LanguageLevel level) {
    PsiFile psiFile =
      PsiFileFactory.getInstance(project).createFileFromText(ARRAY_CLASS_NAME + "." + StdFileTypes.JAVA.getDefaultExtension(),
                                                             StdFileTypes.JAVA.getLanguage(),
                                                             ARRAY_CLASS_TEXT);
    PsiUtil.FILE_LANGUAGE_LEVEL_KEY.set(psiFile, level);
    return ((PsiJavaFile)psiFile).getClasses()[0];
  }

  @Nullable
  public static PsiClass findClass(@NotNull final String className, @NotNull Project project, final GlobalSearchScope scope) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    try {
      if (getArrayClass(className) != null) {
        return createArrayClass(project, LanguageLevelProjectExtension.getInstance(project).getLanguageLevel());
      }
      if (project.isDefault()) {
        return null;
      }

      PsiManager psiManager = PsiManager.getInstance(project);
      PsiClass psiClass = ClassUtil.findPsiClass(psiManager, className, null, true, scope);
      if (psiClass == null) {
        GlobalSearchScope globalScope = GlobalSearchScope.allScope(project);
        if (!globalScope.equals(scope)) {
          psiClass = ClassUtil.findPsiClass(psiManager, className, null, true, globalScope);
        }
      }

      return psiClass;
    }
    catch (IndexNotReadyException ignored) {
      return null;
    }
  }

  @Nullable
  public static PsiType getType(@NotNull String className, @NotNull Project project) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    try {
      if (getArrayClass(className) != null) {
        return JavaPsiFacade.getInstance(project).getElementFactory().createTypeFromText(className, null);
      }
      if (project.isDefault()) {
        return null;
      }
      PsiClass aClass = findClass(className, project, GlobalSearchScope.allScope(project));
      if (aClass != null) {
        return PsiTypesUtil.getClassType(aClass);
      }
    }
    catch (IncorrectOperationException ignored) {
    }
    return null;
  }

  public static void checkSyntax(PsiCodeFragment codeFragment) throws EvaluateException {
    PsiElement[] children = codeFragment.getChildren();

    if(children.length == 0) throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.empty.code.fragment"));
    for (PsiElement child : children) {
      if (child instanceof PsiErrorElement) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", child.getText()));
      }
    }
  }

  public static boolean hasSideEffects(@Nullable PsiElement element) {
    return hasSideEffectsOrReferencesMissingVars(element, null);
  }
  
  public static boolean hasSideEffectsOrReferencesMissingVars(@Nullable PsiElement element, @Nullable final Set<String> visibleLocalVariables) {
    if (element == null) {
      return false;
    }
    final Ref<Boolean> rv = new Ref<>(Boolean.FALSE);
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
          rv.set(Boolean.TRUE);
          //final PsiMethod method = (PsiMethod)psiElement;
          //if (!isSimpleGetter(method)) {
          //  rv.set(Boolean.TRUE);
          //}
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
        rv.set(Boolean.TRUE);
        //final PsiMethod method = callExpression.resolveMethod();
        //if (method == null || !isSimpleGetter(method)) {
        //  rv.set(Boolean.TRUE);
        //}
        //else {
        //  super.visitCallExpression(callExpression);
        //}
      }
    });
    return rv.get().booleanValue();
  }

  public abstract String findAvailableDebugAddress(boolean useSockets) throws ExecutionException;

  public static boolean isSynthetic(@Nullable TypeComponent typeComponent) {
    if (typeComponent == null) {
      return false;
    }
    return Arrays.stream(SyntheticTypeComponentProvider.EP_NAME.getExtensions()).anyMatch(provider -> provider.isSynthetic(typeComponent));
  }

  /**
   * @deprecated use {@link #isInsideSimpleGetter(PsiElement)} instead
   */
  @Deprecated
  public static boolean isSimpleGetter(PsiMethod method) {
    return Arrays.stream(SimpleGetterProvider.EP_NAME.getExtensions()).anyMatch(provider -> provider.isSimpleGetter(method));
  }

  public static boolean isInsideSimpleGetter(@NotNull PsiElement contextElement) {
    for (SimpleGetterProvider provider : SimpleGetterProvider.EP_NAME.getExtensions()) {
      PsiMethod psiMethod = PsiTreeUtil.getParentOfType(contextElement, PsiMethod.class);
      if (psiMethod != null && provider.isSimpleGetter(psiMethod)) return true;
    }
    return Arrays.stream(SimplePropertyGetterProvider.EP_NAME.getExtensions())
      .anyMatch(provider -> provider.isInsideSimpleGetter(contextElement));
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

  /**
   * IDEA-122113
   * Will be removed when Java debugger will be moved to XDebugger API
   */
  public static boolean isDebugActionAware(@NotNull PsiFile file) {
    return isDebugAware(file, false);
  }

  public static boolean isBreakpointAware(@NotNull PsiFile file) {
    return isDebugAware(file, true);
  }

  private static boolean isDebugAware(@NotNull PsiFile file, boolean breakpointAware) {
    FileType fileType = file.getFileType();
    //noinspection deprecation
    if (fileType instanceof LanguageFileType && ((LanguageFileType)fileType).isJVMDebuggingSupported()) {
      return true;
    }

    return Arrays.stream(JavaDebugAware.EP_NAME.getExtensions())
      .anyMatch(provider -> breakpointAware ? provider.isBreakpointAware(file) : provider.isActionAware(file));
  }
}
