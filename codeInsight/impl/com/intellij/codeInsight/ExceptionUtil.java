package com.intellij.codeInsight;

import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author mike
 */
public class ExceptionUtil {
  private static final @NonNls String CLONE_METHOD_NAME = "clone";

  private ExceptionUtil() {}

  public static PsiClassType[] getThrownExceptions(PsiElement[] elements) {
    List<PsiClassType> array = new ArrayList<PsiClassType>();
    for (PsiElement element : elements) {
      PsiClassType[] exceptions = getThrownExceptions(element);
      addExceptions(array, exceptions);
    }

    return array.toArray(new PsiClassType[array.size()]);
  }

  public static PsiClassType[] getThrownCheckedExceptions(PsiElement[] elements) {
    PsiClassType[] exceptions = getThrownExceptions(elements);
    if (exceptions.length == 0) return exceptions;
    exceptions = filterOutUncheckedExceptions(exceptions);
    return exceptions;
  }

  private static PsiClassType[] filterOutUncheckedExceptions(PsiClassType[] exceptions) {
    if (exceptions.length == 0) return exceptions;
    List<PsiClassType> array = new ArrayList<PsiClassType>();
    for (PsiClassType exception : exceptions) {
      if (!isUncheckedException(exception)) array.add(exception);
    }
    return array.toArray(new PsiClassType[array.size()]);
  }

  public static PsiClassType[] getThrownExceptions(PsiElement element) {
    if (element instanceof PsiClass) {
      return PsiClassType.EMPTY_ARRAY; // filter class declaration in code
    }
    else if (element instanceof PsiMethodCallExpression) {
      PsiReferenceExpression methodRef = ((PsiMethodCallExpression)element).getMethodExpression();
      JavaResolveResult result = methodRef.advancedResolve(false);
      return getExceptionsByMethodAndChildren(element, result);
    }
    else if (element instanceof PsiNewExpression) {
      JavaResolveResult result = ((PsiNewExpression)element).resolveMethodGenerics();
      return getExceptionsByMethodAndChildren(element, result);
    }
    else if (element instanceof PsiThrowStatement) {
      PsiExpression expr = ((PsiThrowStatement)element).getException();
      if (expr == null) return PsiClassType.EMPTY_ARRAY;
      PsiType exception = expr.getType();
      List<PsiClassType> array = new ArrayList<PsiClassType>();
      if (exception instanceof PsiClassType) {
        array.add((PsiClassType)exception);
      }
      addExceptions(array, getThrownExceptions(expr));
      return array.toArray(new PsiClassType[array.size()]);
    }
    else if (element instanceof PsiTryStatement) {
      PsiTryStatement tryStatement = (PsiTryStatement)element;
      final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      List<PsiClassType> array = new ArrayList<PsiClassType>();
      if (tryBlock != null) {
        PsiClassType[] exceptions = getThrownExceptions(tryBlock);
        for (PsiClassType exception : exceptions) {
          array.add(exception);
        }
      }

      PsiParameter[] parameters = tryStatement.getCatchBlockParameters();
      for (PsiParameter parm : parameters) {
        PsiType exception = parm.getType();
        for (int j = array.size() - 1; j >= 0; j--) {
          PsiClassType exception1 = array.get(j);
          if (exception.isAssignableFrom(exception1)) {
            array.remove(exception1);
          }
        }
      }

      PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
      for (PsiCodeBlock catchBlock : catchBlocks) {
        addExceptions(array, getThrownExceptions(catchBlock));
      }

      PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock != null) {
        // if finally block completes normally, exception not catched
        // if finally block completes abruptly, exception gets lost
        try {
          ControlFlow flow = ControlFlowFactory.getControlFlow(finallyBlock, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
          int completionReasons = ControlFlowUtil.getCompletionReasons(flow, 0, flow.getSize());
          PsiClassType[] thrownExceptions = getThrownExceptions(finallyBlock);
          if ((completionReasons & ControlFlowUtil.NORMAL_COMPLETION_REASON) == 0) {
            array = new ArrayList<PsiClassType>(Arrays.asList(thrownExceptions));
          }
          else {
            addExceptions(array, thrownExceptions);
          }
        }
        catch (AnalysisCanceledException e) {
          // incomplete code
        }
      }

      return array.toArray(new PsiClassType[array.size()]);
    }
    else {
      return getThrownExceptions(element.getChildren());
    }
  }

  private static PsiClassType[] getExceptionsByMethodAndChildren(PsiElement element, JavaResolveResult resolveResult) {
    PsiMethod method = (PsiMethod)resolveResult.getElement();
    List<PsiClassType> result = new ArrayList<PsiClassType>();
    if (method != null) {
      PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      final PsiClassType[] referenceTypes = method.getThrowsList().getReferencedTypes();
      for (PsiType type : referenceTypes) {
        type = substitutor.substitute(type);
        if (type instanceof PsiClassType) {
          result.add((PsiClassType)type);
        }
      }
    }

    PsiElement[] children = element.getChildren();
    for (PsiElement child : children) {
      addExceptions(result, getThrownExceptions(child));
    }
    return result.toArray(new PsiClassType[result.size()]);
  }

  private static void addExceptions(List<PsiClassType> array, PsiClassType[] exceptions) {
    for (PsiClassType exception : exceptions) {
      addException(array, exception);
    }
  }

  private static void addException(List<PsiClassType> array, PsiClassType exception) {
    for (int i = array.size()-1; i>=0; i--) {
      PsiClassType exception1 = array.get(i);
      if (exception1.isAssignableFrom(exception)) return;
      if (exception.isAssignableFrom(exception1)) {
        array.remove(i);
      }
    }
    array.add(exception);
  }

  public static PsiClassType[] collectUnhandledExceptions(PsiElement element, PsiElement topElement) {
    final Set<PsiClassType> set = collectUnhandledExceptions(element, topElement, null);
    return set == null ? PsiClassType.EMPTY_ARRAY : set.toArray(new PsiClassType[set.size()]);
  }

  private static Set<PsiClassType> collectUnhandledExceptions(PsiElement element, PsiElement topElement, Set<PsiClassType> foundExceptions) {
    PsiClassType[] unhandledExceptions = null;
    if (element instanceof PsiCallExpression) {
      PsiCallExpression expression = (PsiCallExpression)element;
      unhandledExceptions = getUnhandledExceptions(expression, topElement);
    }
    else if (element instanceof PsiThrowStatement) {
      PsiThrowStatement statement = (PsiThrowStatement)element;
      unhandledExceptions = getUnhandledExceptions(statement, topElement);
    }
    else if (element instanceof PsiCodeBlock
             && element.getParent() instanceof PsiMethod
             && ((PsiMethod)element.getParent()).isConstructor()
             && !firstStatementIsConstructorCall((PsiCodeBlock)element)) {
      // there is implicit parent constructor call
      final PsiMethod constructor = (PsiMethod)element.getParent();
      final PsiClass aClass = constructor.getContainingClass();
      final PsiClass superClass = aClass == null ? null : aClass.getSuperClass();
      final PsiMethod[] superConstructors = superClass == null ? PsiMethod.EMPTY_ARRAY : superClass.getConstructors();
      Set<PsiClassType> unhandled = new HashSet<PsiClassType>();
      for (PsiMethod superConstructor : superConstructors) {
        if (!superConstructor.hasModifierProperty(PsiModifier.PRIVATE) &&
            superConstructor.getParameterList().getParameters().length == 0) {
          final PsiClassType[] exceptionTypes = superConstructor.getThrowsList().getReferencedTypes();
          for (PsiClassType exceptionType : exceptionTypes) {
            if (!isUncheckedException(exceptionType) && !isHandled(element, exceptionType, topElement)) {
              unhandled.add(exceptionType);
            }
          }
          break;
        }
      }

      // plus all exceptions thrown in instance class initializers
      if (aClass != null) {
        final PsiClassInitializer[] initializers = aClass.getInitializers();
        final Set<PsiClassType> thrownByInitializer = new THashSet<PsiClassType>();
        for (PsiClassInitializer initializer : initializers) {
          if (initializer.hasModifierProperty(PsiModifier.STATIC)) continue;
          thrownByInitializer.clear();
          collectUnhandledExceptions(initializer.getBody(), initializer, thrownByInitializer);
          for (PsiClassType thrown : thrownByInitializer) {
            if (!isHandled(constructor.getBody(), thrown, topElement)) {
              unhandled.add(thrown);
            }
          }
        }
      }
      unhandledExceptions = unhandled.toArray(new PsiClassType[unhandled.size()]);
    }

    if (unhandledExceptions != null) {
      if (foundExceptions == null) {
        foundExceptions = new HashSet<PsiClassType>();
      }
      for (PsiClassType unhandledException : unhandledExceptions) {
        foundExceptions.add(unhandledException);
      }
    }

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      foundExceptions = collectUnhandledExceptions(child, topElement, foundExceptions);
    }

    return foundExceptions;
  }

  private static boolean firstStatementIsConstructorCall(PsiCodeBlock constructorBody) {
    final PsiStatement[] statements = constructorBody.getStatements();
    if (statements.length == 0) return false;
    if (!(statements[0] instanceof PsiExpressionStatement)) return false;

    final PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
    if (!(expression instanceof PsiMethodCallExpression)) return false;
    final PsiMethod method = (PsiMethod)((PsiMethodCallExpression)expression).getMethodExpression().resolve();
    if (method == null || !method.isConstructor()) return false;

    return true;
  }

  public static PsiClassType[] getUnhandledExceptions(PsiElement[] elements) {
    final List<PsiClassType> array = new ArrayList<PsiClassType>();
    PsiRecursiveElementVisitor visitor = new PsiRecursiveElementVisitor() {
      public void visitCallExpression(PsiCallExpression expression) {
        addExceptions(array, getUnhandledExceptions(expression, null));
        visitElement(expression);
      }

      public void visitThrowStatement(PsiThrowStatement statement) {
        addExceptions(array, getUnhandledExceptions(statement, null));
        visitElement(statement);
      }
    };

    for (PsiElement element : elements) {
      element.accept(visitor);
    }

    return array.toArray(new PsiClassType[array.size()]);
  }

  public static PsiClassType[] getUnhandledExceptions(PsiElement element) {
    if (element instanceof PsiCallExpression) {
      PsiCallExpression expression = (PsiCallExpression)element;
      return getUnhandledExceptions(expression, null);
    }
    else if (element instanceof PsiThrowStatement) {
      PsiThrowStatement throwStatement = (PsiThrowStatement)element;
      return getUnhandledExceptions(throwStatement, null);
    }

    return PsiClassType.EMPTY_ARRAY;
  }

  public static PsiClassType[] getUnhandledExceptions(PsiCallExpression methodCall, PsiElement topElement) {
    final JavaResolveResult result = methodCall.resolveMethodGenerics();
    PsiMethod method = (PsiMethod)result.getElement();
    return getUnhandledExceptions(method, methodCall, topElement, result.getSubstitutor());
  }

  public static PsiClassType[] getUnhandledExceptions(PsiThrowStatement throwStatement, PsiElement topElement) {
    final PsiExpression exception = throwStatement.getException();
    if (exception != null) {
      final PsiType type = exception.getType();
      if (type instanceof PsiClassType) {
        PsiClassType classType = (PsiClassType)type;
        if (!isUncheckedException(classType) && !isHandled(throwStatement, classType, topElement)) {
          return new PsiClassType[]{classType};
        }
      }
    }
    return PsiClassType.EMPTY_ARRAY;
  }


  private static PsiClassType[] getUnhandledExceptions(PsiMethod method,
                                                       PsiElement element,
                                                       PsiElement topElement,
                                                       PsiSubstitutor substitutor) {
    if (method == null || isArrayClone(method, element)) {
      return PsiClassType.EMPTY_ARRAY;
    }
    final PsiClassType[] referencedTypes = method.getThrowsList().getReferencedTypes();
    if (referencedTypes.length > 0) {
      List<PsiClassType> result = new ArrayList<PsiClassType>();

      for (PsiClassType referencedType : referencedTypes) {
        final PsiType type = substitutor.substitute(referencedType);
        if (!(type instanceof PsiClassType)) continue;
        PsiClassType classType = (PsiClassType)type;
        PsiClass exceptionClass = ((PsiClassType)type).resolve();
        if (exceptionClass == null) continue;

        if (isUncheckedException(classType)) continue;
        if (isHandled(element, classType, topElement)) continue;

        result.add((PsiClassType)type);
      }

      return result.toArray(new PsiClassType[result.size()]);
    }
    return PsiClassType.EMPTY_ARRAY;
  }

  private static boolean isArrayClone(PsiMethod method, PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression)) return false;
    if (!method.getName().equals(CLONE_METHOD_NAME)) return false;
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || !"java.lang.Object".equals(containingClass.getQualifiedName())) {
      return false;
    }

    PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
    final PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
    if (qualifierExpression == null) return false;
    if (qualifierExpression.getType() instanceof PsiArrayType) return true;

    return false;
  }

  public static boolean isUncheckedException(PsiClassType type) {
    GlobalSearchScope searchScope = type.getResolveScope();
    PsiClass aClass = type.resolve();
    if (aClass == null) return false;
    PsiClass runtimeExceptionClass = aClass.getManager().findClass("java.lang.RuntimeException", searchScope);
    if (runtimeExceptionClass != null &&
        InheritanceUtil.isInheritorOrSelf(aClass, runtimeExceptionClass, true)) {
      return true;
    }

    PsiClass errorClass = aClass.getManager().findClass("java.lang.Error", searchScope);
    if (errorClass != null && InheritanceUtil.isInheritorOrSelf(aClass, errorClass, true)) return true;

    return false;
  }

  public static boolean isUncheckedExceptionOrSuperclass(PsiClassType type) {
    if (type == null) return false;
    String canonicalText = type.getCanonicalText();
    return "java.lang.Throwable".equals(canonicalText) ||
           "java.lang.Exception".equals(canonicalText) ||
           isUncheckedException(type);
  }

  public static boolean isHandled(PsiClassType exceptionType, PsiElement throwPlace) {
    return isHandled(throwPlace, exceptionType, throwPlace.getContainingFile());
  }

  private static boolean isHandled(PsiElement element, PsiClassType exceptionType, PsiElement topElement) {
    if (element == null || element.getParent() == topElement || element.getParent() == null) return false;

    final PsiElement parent = element.getParent();

    if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      return isHandledByMethodThrowsClause(method, exceptionType);
    }
    else if (parent instanceof PsiClass) {
      if (parent instanceof PsiAnonymousClass /*&& element instanceof PsiExpressionList*/) {
        // arguments to anon class constructor should be handled higher
        // like in void f() throws XXX { new AA(methodThrowingXXX()) { ... }; }
        return isHandled(parent, exceptionType, topElement);
      }
      return false;
    }
    else if (parent instanceof PsiClassInitializer) {
      if (((PsiClassInitializer)parent).hasModifierProperty(PsiModifier.STATIC)) return false;
      // anonymous class initializers can throw any exceptions
      if (!(parent.getParent() instanceof PsiAnonymousClass)) {
        // exception thrown from within class instance initializer must be handled in every class constructor
        // check each constructor throws exception or superclass (there must be at least one)
        final PsiClass aClass = ((PsiClassInitializer)parent).getContainingClass();
        return isAllConstructorsThrow(aClass, exceptionType);
      }
    }
    else if (parent instanceof PsiTryStatement) {
      PsiTryStatement tryStatement = (PsiTryStatement)parent;
      if (tryStatement.getTryBlock() == element && isCatched(tryStatement, exceptionType)) {
        return true;
      }
      else {
        return isHandled(parent, exceptionType, topElement);
      }
    }
    else if (parent instanceof PsiCodeFragment) {
      PsiCodeFragment codeFragment = (PsiCodeFragment)parent;
      if (codeFragment.getExceptionHandler() == null) return false;
      return codeFragment.getExceptionHandler().isHandledException(exceptionType);
    }
    else if (PsiUtil.isInJspFile(parent) && parent instanceof PsiFile) {
      return true;
    }
    else if (parent instanceof PsiFile) {
      return false;
    }
    else if (parent instanceof PsiField && ((PsiField)parent).getInitializer() == element) {
      final PsiClass aClass = ((PsiField)parent).getContainingClass();
      if (aClass != null && !(aClass instanceof PsiAnonymousClass) && !((PsiField)parent).hasModifierProperty(PsiModifier.STATIC)) {
        // exceptions thrown in field initalizers should be thrown in all class constructors
        return isAllConstructorsThrow(aClass, exceptionType);
      }
    }
    return isHandled(parent, exceptionType, topElement);
  }

  private static boolean isAllConstructorsThrow(final PsiClass aClass, PsiClassType exceptionType) {
    if (aClass == null) return false;
    final PsiMethod[] constructors = aClass.getConstructors();
    boolean thrown = constructors.length != 0;
    for (PsiMethod constructor : constructors) {
      if (!isHandledByMethodThrowsClause(constructor, exceptionType)) {
        thrown = false;
        break;
      }
    }
    return thrown;
  }

  private static boolean isCatched(PsiTryStatement tryStatement, PsiClassType exceptionType) {
    PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null) {
      PsiClassType[] exceptions = getUnhandledExceptions(finallyBlock);
      List<PsiClassType> unhandledFinallyExceptions = Arrays.asList(exceptions);
      if (unhandledFinallyExceptions.contains(exceptionType)) return false;
      // if finally block completes normally, exception not catched
      // if finally block completes abruptly, exception gets lost
      try {
        ControlFlow flow = ControlFlowFactory.getControlFlow(finallyBlock, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(),
                                                             false);
        int completionReasons = ControlFlowUtil.getCompletionReasons(flow, 0, flow.getSize());
        if ((completionReasons & ControlFlowUtil.NORMAL_COMPLETION_REASON) == 0) return true;
      }
      catch (AnalysisCanceledException e) {
        return true;
      }
    }

    final PsiParameter[] catchBlockParameters = tryStatement.getCatchBlockParameters();
    for (PsiParameter parameter : catchBlockParameters) {
      PsiType paramType = parameter.getType();
      if (paramType.isAssignableFrom(exceptionType)) return true;
    }

    return false;
  }

  public static boolean isHandledByMethodThrowsClause(PsiMethod method, PsiClassType exceptionType) {
    final PsiClassType[] referencedTypes = method.getThrowsList().getReferencedTypes();
    return isHandledBy(exceptionType, referencedTypes);
  }

  public static boolean isHandledBy(PsiClassType exceptionType, final PsiClassType[] referencedTypes) {
    if (referencedTypes == null) return false;

    for (PsiClassType classType : referencedTypes) {
      if (classType.isAssignableFrom(exceptionType)) return true;
    }

    return false;
  }
}
