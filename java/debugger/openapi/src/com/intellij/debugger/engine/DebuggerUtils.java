// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public abstract class DebuggerUtils {
  private static final Logger LOG = Logger.getInstance(DebuggerUtils.class);
  public static final Set<String> ourPrimitiveTypeNames = Set.of(
    "byte", "short", "int", "long", "float", "double", "boolean", "char"
  );

  public enum HowToSwitchToSuspendAll {
    IMMEDIATE_PAUSE, PAUSE_WAITING_EVALUATION, METHOD_BREAKPOINT, DISABLE
  }

  public static HowToSwitchToSuspendAll howToSwitchToSuspendAll() {
    String howToSwitchStr = Registry.get("debugger.how.to.switch.to.suspend.all").getSelectedOption();
    return HowToSwitchToSuspendAll.valueOf(howToSwitchStr);
  }

  public static boolean isAlwaysSuspendThreadBeforeSwitch() {
    return howToSwitchToSuspendAll() != HowToSwitchToSuspendAll.DISABLE;
  }

  public static boolean isNewThreadSuspendStateTracking() {
    return Registry.is("debugger.new.suspend.state.tracking");
  }

  public static @NonNls String getValueAsString(@NotNull EvaluationContext evaluationContext, @Nullable Value value) throws EvaluateException {
    return getInstance().getValueAsStringImpl(evaluationContext, value);
  }

  protected abstract @NonNls String getValueAsStringImpl(@NotNull EvaluationContext evaluationContext, @Nullable Value value) throws EvaluateException;

  @ApiStatus.Internal
  public abstract <R, T> R processCollectibleValue(
    @NotNull ThrowableComputable<? extends T, ? extends EvaluateException> valueComputable,
    @NotNull Function<? super T, ? extends R> processor,
    @NotNull EvaluationContext evaluationContext) throws EvaluateException;

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

  public static @Nullable Method findMethod(@NotNull ReferenceType refType, @NonNls String methodName, @Nullable @NonNls String methodSignature) {
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
      //noinspection SSBasedInspection
      method = ContainerUtil.getFirstItem(
        methodSignature != null ? refType.methodsByName(methodName, methodSignature) : refType.methodsByName(methodName));
    }
    return method;
  }

  /**
   * Optimized version of {@link ClassType#concreteMethodByName(String, String)}.
   * It does not gather all visible methods before checking so can return early
   */
  private static @Nullable Method concreteMethodByName(@NotNull ClassType type, @NotNull String name, @Nullable String signature) {
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

  /**
   * Optimized version of {@link ReferenceType#fieldByName(String)}.
   * It does not gather all visible fields before checking so can return early
   */
  public static @Nullable Field findField(@NotNull ReferenceType type, @NotNull String name) {
    LinkedList<ReferenceType> types = new LinkedList<>();
    // first check classes
    while (type != null) {
      for (Field candidate : type.fields()) {
        if (candidate.name().equals(name)) {
          return candidate;
        }
      }
      types.add(type);
      type = type instanceof ClassType classType ? classType.superclass() : null;
    }
    // then interfaces
    Set<ReferenceType> checkedInterfaces = new HashSet<>();
    ReferenceType t;
    while ((t = types.poll()) != null) {
      if (t instanceof ClassType) {
        types.addAll(0, ((ClassType)t).interfaces());
      }
      else if (t instanceof InterfaceType && checkedInterfaces.add(t)) {
        for (Field candidate : t.fields()) {
          if (candidate.name().equals(name)) {
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

  protected static @Nullable ArrayClass getArrayClass(@NotNull String className) {
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

  public static @Nullable Type getSuperType(@Nullable Type subType, @NotNull String superType) {
    if (subType == null || subType instanceof PrimitiveType || subType instanceof VoidType) {
      return null;
    }

    if (CommonClassNames.JAVA_LANG_OBJECT.equals(superType)) {
      return getObjectClassType(subType.virtualMachine());
    }

    return getSuperTypeInt(subType, superType);
  }

  protected static ReferenceType getObjectClassType(VirtualMachine virtualMachine) {
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

  public static @Nullable PsiClass findClass(@NotNull String className, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    return findClass(className, project, scope, true);
  }

  public static @Nullable PsiClass findClass(@NotNull String className,
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

  protected abstract @Nullable GlobalSearchScope getFallbackAllScope(@NotNull GlobalSearchScope scope, @NotNull Project project);

  public static @Nullable PsiType getType(@NotNull String className, @NotNull Project project) {
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
                                                              final @Nullable Set<String> visibleLocalVariables) {
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

  @ApiStatus.Internal
  public static @Nullable String tryExtractExceptionMessage(@NotNull ObjectReference exception) {
    final ReferenceType type = exception.referenceType();
    final Field messageField = findField(type, "detailMessage");
    if (messageField == null) return null;
    final Value message = exception.getValue(messageField);
    if (message instanceof StringReference) {
      return ((StringReference)message).value();
    }

    return null;
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
    if (fileType instanceof LanguageFileType lft && lft.isJVMDebuggingSupported()) {
      return true;
    }

    return ContainerUtil.exists(JavaDebugAware.EP_NAME.getExtensionList(), provider -> provider.isBreakpointAware(file));
  }

  public static boolean isAndroidVM(@NotNull VirtualMachine virtualMachine) {
    return StringUtil.containsIgnoreCase(virtualMachine.name(), "dalvik");
  }
}
