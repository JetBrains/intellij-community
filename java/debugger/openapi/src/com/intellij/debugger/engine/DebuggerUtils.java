// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public abstract class DebuggerUtils {
  private static final Logger LOG = Logger.getInstance(DebuggerUtils.class);
  private static final Key<Method> TO_STRING_METHOD_KEY = new Key<>("CachedToStringMethod");
  public static final Set<String> ourPrimitiveTypeNames = Set.of(
    "byte", "short", "int", "long", "float", "double", "boolean", "char"
  );

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
        ensureNotInsideObjectConstructor((ObjectReference)value, evaluationContext);
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
      if (value instanceof ObjectReference objRef) {
        if (value instanceof ArrayReference arrayRef) {
          final StringJoiner joiner = new StringJoiner(",", "[", "]");
          for (final Value element : arrayRef.getValues()) {
            joiner.add(getValueAsString(evaluationContext, element));
          }
          return joiner.toString();
        }

        final DebugProcess debugProcess = evaluationContext.getDebugProcess();
        Method toStringMethod = debugProcess.getUserData(TO_STRING_METHOD_KEY);
        if (toStringMethod == null || !toStringMethod.virtualMachine().equals(objRef.virtualMachine())) {
          try {
            ReferenceType refType = getObjectClassType(objRef.virtualMachine());
            toStringMethod = findMethod(refType, "toString", "()Ljava/lang/String;");
            debugProcess.putUserData(TO_STRING_METHOD_KEY, toStringMethod);
          }
          catch (Exception ignored) {
            throw EvaluateExceptionUtil.createEvaluateException(
              JavaDebuggerBundle.message("evaluation.error.cannot.evaluate.tostring", objRef.referenceType().name()));
          }
        }
        if (toStringMethod == null) {
          throw EvaluateExceptionUtil.createEvaluateException(
            JavaDebuggerBundle.message("evaluation.error.cannot.evaluate.tostring", objRef.referenceType().name()));
        }
        Method finalToStringMethod = toStringMethod;
        return processCollectibleValue(
          () -> debugProcess.invokeInstanceMethod(evaluationContext, objRef, finalToStringMethod, Collections.emptyList(), 0),
          result -> {
            // while result must be of com.sun.jdi.StringReference type, it turns out that sometimes (jvm bugs?)
            // it is a plain com.sun.tools.jdi.ObjectReferenceImpl
            if (result == null) {
              return "null";
            }
            return result instanceof StringReference ? ((StringReference)result).value() : result.toString();
          }
        );
      }
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.unsupported.expression.type"));
    }
    catch (ObjectCollectedException ignored) {
      throw EvaluateExceptionUtil.OBJECT_WAS_COLLECTED;
    }
  }

  public static <R, T extends Value> R processCollectibleValue(
    @NotNull ThrowableComputable<? extends T, ? extends EvaluateException> valueComputable,
    @NotNull Function<? super T, ? extends R> processor) throws EvaluateException {
    int retries = 10;
    while (true) {
      T result = valueComputable.compute();
      try {
        return processor.apply(result);
      }
      catch (ObjectCollectedException oce) {
        if (--retries < 0) {
          throw oce;
        }
      }
    }
  }

  public static void ensureNotInsideObjectConstructor(@NotNull ObjectReference reference, @NotNull EvaluationContext context)
    throws EvaluateException {
    Location location = getInstance().getLocation(context.getSuspendContext());
    if (location != null && location.method().isConstructor() && reference.equals(context.computeThisObject())) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.object.is.being.initialized"));
    }
  }

  protected abstract Location getLocation(SuspendContext context);

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
    // speedup the search by not gathering all methods through the class hierarchy
    if (refType instanceof ClassType) {
      method = concreteMethodByName((ClassType)refType, methodName, methodSignature);
    }
    if (method == null) {
      method = ContainerUtil.getFirstItem(
        methodSignature != null ? refType.methodsByName(methodName, methodSignature) : refType.methodsByName(methodName));
    }
    return method;
  }

  /**
   * Optimized version of {@link ClassType#concreteMethodByName(String, String)}.
   * It does not gather all visible methods before checking so can return early
   */
  @Nullable
  private static Method concreteMethodByName(@NotNull ClassType type, @NotNull String name, @Nullable String signature) {
    Processor<Method> signatureChecker = signature != null ? m -> m.signature().equals(signature) : CommonProcessors.alwaysTrue();
    LinkedList<ReferenceType> types = new LinkedList<>();
    // first check classes
    while (type != null) {
      for (Method candidate : type.methods()) {
        if (candidate.name().equals(name) && signatureChecker.process(candidate)) {
          return !candidate.isAbstract() ? candidate : null;
        }
      }
      types.add(type);
      type = type.superclass();
    }
    // then interfaces
    Set<ReferenceType> checkedInterfaces = new HashSet<>();
    ReferenceType t;
    while ((t = types.poll()) != null) {
      if (t instanceof ClassType) {
        types.addAll(0, ((ClassType)t).interfaces());
      }
      else if (t instanceof InterfaceType && checkedInterfaces.add(t)) {
        for (Method candidate : t.methods()) {
          if (candidate.name().equals(name) && signatureChecker.process(candidate) && !candidate.isAbstract()) {
            return candidate;
          }
        }
        types.addAll(0, ((InterfaceType)t).superinterfaces());
      }
    }
    return null;
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

    for (pos = className.lastIndexOf(']'); pos >= 0; pos--) {
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

    if (dims == 0) return null;

    return new ArrayClass(className.substring(0, pos + 1), dims);
  }

  public static boolean instanceOf(@NotNull String subType, @NotNull String superType, @Nullable Project project) {
    if (project == null) {
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

    if (subType instanceof ArrayType &&
        (CommonClassNames.JAVA_LANG_CLONEABLE.equals(superType) || CommonClassNames.JAVA_IO_SERIALIZABLE.equals(superType))) {
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

        for (InterfaceType iface : clsType.interfaces()) {
          result = getSuperType(iface, superType);
          if (result != null) {
            return result;
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

  // workaround to get an array class of needed language version for correct HL in array renderers expression
  protected abstract PsiClass createArrayClass(Project project, LanguageLevel level);

  @Nullable
  public static PsiClass findClass(@NotNull String className, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    return findClass(className, project, scope, true);
  }

  @Nullable
  public static PsiClass findClass(@NotNull String className,
                                   @NotNull Project project,
                                   @NotNull GlobalSearchScope scope,
                                   boolean fallbackToAllScope) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    try {
      if (getArrayClass(className) != null) {
        return getInstance().createArrayClass(project, LanguageLevelProjectExtension.getInstance(project).getLanguageLevel());
      }
      if (project.isDefault()) {
        return null;
      }

      // remove generics if any
      className = StringUtil.notNullize(StringUtil.substringBefore(className, "<"), className);

      PsiManager psiManager = PsiManager.getInstance(project);
      PsiClass psiClass = ClassUtil.findPsiClass(psiManager, className, null, true, scope);
      if (psiClass == null && fallbackToAllScope) {
        GlobalSearchScope globalScope = getInstance().getFallbackAllScope(scope, project);
        if (globalScope != null) {
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
  protected abstract GlobalSearchScope getFallbackAllScope(@NotNull GlobalSearchScope scope, @NotNull Project project);

  @Nullable
  public static PsiType getType(@NotNull String className, @NotNull Project project) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    try {
      if (getArrayClass(className) != null) {
        return JavaPsiFacade.getElementFactory(project).createTypeFromText(className, null);
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

    if (children.length == 0) {
      throw EvaluateExceptionUtil.createEvaluateException(
        JavaDebuggerBundle.message("evaluation.error.empty.code.fragment"));
    }
    for (PsiElement child : children) {
      if (child instanceof PsiErrorElement) {
        throw EvaluateExceptionUtil.createEvaluateException(
          JavaDebuggerBundle.message("evaluation.error.invalid.expression", child.getText()));
      }
    }
  }

  public static boolean hasSideEffects(@Nullable PsiElement element) {
    return hasSideEffectsOrReferencesMissingVars(element, null);
  }

  public static boolean hasSideEffectsOrReferencesMissingVars(@Nullable PsiElement element,
                                                              @Nullable final Set<String> visibleLocalVariables) {
    if (element == null) {
      return false;
    }
    final Ref<Boolean> rv = new Ref<>(Boolean.FALSE);
    element.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitPostfixExpression(final @NotNull PsiPostfixExpression expression) {
        rv.set(Boolean.TRUE);
      }

      @Override
      public void visitReferenceExpression(final @NotNull PsiReferenceExpression expression) {
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
      public void visitPrefixExpression(final @NotNull PsiPrefixExpression expression) {
        final IElementType op = expression.getOperationTokenType();
        if (JavaTokenType.PLUSPLUS.equals(op) || JavaTokenType.MINUSMINUS.equals(op)) {
          rv.set(Boolean.TRUE);
        }
        else {
          super.visitPrefixExpression(expression);
        }
      }

      @Override
      public void visitAssignmentExpression(final @NotNull PsiAssignmentExpression expression) {
        rv.set(Boolean.TRUE);
      }

      @Override
      public void visitCallExpression(final @NotNull PsiCallExpression callExpression) {
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
    if (ContainerUtil.exists(SyntheticTypeComponentProvider.EP_NAME.getExtensionList(),
                             provider -> provider.isNotSynthetic(typeComponent))) {
      return false;
    }
    return ContainerUtil.exists(SyntheticTypeComponentProvider.EP_NAME.getExtensionList(), provider -> provider.isSynthetic(typeComponent));
  }

  public static boolean isInsideSimpleGetter(@NotNull PsiElement contextElement) {
    return ContainerUtil.exists(SimplePropertyGetterProvider.EP_NAME.getExtensionList(),
                                provider -> provider.isInsideSimpleGetter(contextElement));
  }

  public static boolean isPrimitiveType(String typeName) {
    return typeName != null && ourPrimitiveTypeNames.contains(typeName);
  }

  protected record ArrayClass(String className, int dims) {
  }

  public static DebuggerUtils getInstance() {
    return ApplicationManager.getApplication().getService(DebuggerUtils.class);
  }

  public abstract PsiExpression substituteThis(PsiExpression expressionWithThis,
                                               PsiExpression howToEvaluateThis,
                                               Value howToEvaluateThisValue,
                                               StackFrameContext context) throws EvaluateException;

  public abstract DebuggerContext getDebuggerContext(DataContext context);

  public abstract Element writeTextWithImports(TextWithImports text);

  public abstract TextWithImports readTextWithImports(Element element);

  public abstract void writeTextWithImports(Element root, @NonNls String name, TextWithImports value);

  public abstract TextWithImports readTextWithImports(Element root, @NonNls String name);

  public abstract TextWithImports createExpressionWithImports(@NonNls String expression);

  public abstract PsiElement getContextElement(final StackFrameContext context);

  public abstract PsiClass chooseClassDialog(String title, Project project);

  public static boolean isBreakpointAware(@NotNull PsiFile file) {
    FileType fileType = file.getFileType();
    //noinspection deprecation
    if (fileType instanceof LanguageFileType && ((LanguageFileType)fileType).isJVMDebuggingSupported()) {
      return true;
    }

    return ContainerUtil.exists(JavaDebugAware.EP_NAME.getExtensionList(), provider -> provider.isBreakpointAware(file));
  }

  public static boolean isAndroidVM(@NotNull VirtualMachine virtualMachine) {
    return StringUtil.containsIgnoreCase(virtualMachine.name(), "dalvik");
  }
}
