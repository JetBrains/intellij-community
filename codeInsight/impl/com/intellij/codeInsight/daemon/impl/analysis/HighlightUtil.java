/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 30, 2002
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.problems.ProblemImpl;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.scope.processor.VariablesNotProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.SmartList;
import com.intellij.xml.util.XmlUtil;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class HighlightUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil");
  private static final Map<String, Set<String>> ourInterfaceIncompatibleModifiers;
  public static final Map<String, Set<String>> ourMethodIncompatibleModifiers;
  private static final Map<String, Set<String>> ourFieldIncompatibleModifiers;
  private static final Map<String, Set<String>> ourClassIncompatibleModifiers;
  private static final Map<String, Set<String>> ourClassInitializerIncompatibleModifiers;
  private static final Set<String> ourConstructorNotAllowedModifiers;

  private static final Key<String> HAS_OVERFLOW_IN_CHILD = Key.create("HAS_OVERFLOW_IN_CHILD");
  private static final @NonNls String SERIAL_VERSION_UID_FIELD_NAME = "serialVersionUID";
  private static final @NonNls String SERIAL_PERSISTENT_FIELDS_FIELD_NAME = "serialPersistentFields";
  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  private HighlightUtil() {
  }

  static {
    ourClassIncompatibleModifiers = new THashMap<String, Set<String>>(8);
    Set<String> modifiers = new THashSet<String>(1);
    modifiers.add(PsiModifier.FINAL);
    ourClassIncompatibleModifiers.put(PsiModifier.ABSTRACT, modifiers);
    modifiers = new THashSet<String>(1);
    modifiers.add(PsiModifier.ABSTRACT);
    ourClassIncompatibleModifiers.put(PsiModifier.FINAL, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PROTECTED);
    ourClassIncompatibleModifiers.put(PsiModifier.PACKAGE_LOCAL, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PROTECTED);
    ourClassIncompatibleModifiers.put(PsiModifier.PRIVATE, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PROTECTED);
    ourClassIncompatibleModifiers.put(PsiModifier.PUBLIC, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PRIVATE);
    ourClassIncompatibleModifiers.put(PsiModifier.PROTECTED, modifiers);
    ourClassIncompatibleModifiers.put(PsiModifier.STRICTFP, Collections.<String>emptySet());
    ourClassIncompatibleModifiers.put(PsiModifier.STATIC, Collections.<String>emptySet());
    ourInterfaceIncompatibleModifiers = new THashMap<String, Set<String>>(7);
    ourInterfaceIncompatibleModifiers.put(PsiModifier.ABSTRACT, Collections.<String>emptySet());
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PROTECTED);
    ourInterfaceIncompatibleModifiers.put(PsiModifier.PACKAGE_LOCAL, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PROTECTED);
    ourInterfaceIncompatibleModifiers.put(PsiModifier.PRIVATE, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PROTECTED);
    ourInterfaceIncompatibleModifiers.put(PsiModifier.PUBLIC, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    ourInterfaceIncompatibleModifiers.put(PsiModifier.PROTECTED, modifiers);
    ourInterfaceIncompatibleModifiers.put(PsiModifier.STRICTFP, Collections.<String>emptySet());
    ourInterfaceIncompatibleModifiers.put(PsiModifier.STATIC, Collections.<String>emptySet());
    ourMethodIncompatibleModifiers = new THashMap<String, Set<String>>(10);
    modifiers = new THashSet<String>(6);
    modifiers.addAll(Arrays.asList(PsiModifier.NATIVE, PsiModifier.STATIC, PsiModifier.FINAL, PsiModifier.PRIVATE, PsiModifier.STRICTFP,
                                   PsiModifier.SYNCHRONIZED));
    ourMethodIncompatibleModifiers.put(PsiModifier.ABSTRACT, modifiers);
    modifiers = new THashSet<String>(2);
    modifiers.add(PsiModifier.ABSTRACT);
    modifiers.add(PsiModifier.STRICTFP);
    ourMethodIncompatibleModifiers.put(PsiModifier.NATIVE, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PROTECTED);
    ourMethodIncompatibleModifiers.put(PsiModifier.PACKAGE_LOCAL, modifiers);
    modifiers = new THashSet<String>(4);
    modifiers.add(PsiModifier.ABSTRACT);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PROTECTED);
    ourMethodIncompatibleModifiers.put(PsiModifier.PRIVATE, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PROTECTED);
    ourMethodIncompatibleModifiers.put(PsiModifier.PUBLIC, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PRIVATE);
    ourMethodIncompatibleModifiers.put(PsiModifier.PROTECTED, modifiers);
    modifiers = new THashSet<String>(1);
    modifiers.add(PsiModifier.ABSTRACT);
    ourMethodIncompatibleModifiers.put(PsiModifier.STATIC, modifiers);
    ourMethodIncompatibleModifiers.put(PsiModifier.SYNCHRONIZED, modifiers);
    ourMethodIncompatibleModifiers.put(PsiModifier.STRICTFP, modifiers);
    ourMethodIncompatibleModifiers.put(PsiModifier.FINAL, modifiers);
    ourFieldIncompatibleModifiers = new THashMap<String, Set<String>>(8);
    modifiers = new THashSet<String>(1);
    modifiers.add(PsiModifier.VOLATILE);
    ourFieldIncompatibleModifiers.put(PsiModifier.FINAL, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PROTECTED);
    ourFieldIncompatibleModifiers.put(PsiModifier.PACKAGE_LOCAL, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.PROTECTED);
    ourFieldIncompatibleModifiers.put(PsiModifier.PRIVATE, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PROTECTED);
    ourFieldIncompatibleModifiers.put(PsiModifier.PUBLIC, modifiers);
    modifiers = new THashSet<String>(3);
    modifiers.add(PsiModifier.PACKAGE_LOCAL);
    modifiers.add(PsiModifier.PRIVATE);
    modifiers.add(PsiModifier.PUBLIC);
    ourFieldIncompatibleModifiers.put(PsiModifier.PROTECTED, modifiers);
    ourFieldIncompatibleModifiers.put(PsiModifier.STATIC, Collections.<String>emptySet());
    ourFieldIncompatibleModifiers.put(PsiModifier.TRANSIENT, Collections.<String>emptySet());
    modifiers = new THashSet<String>(1);
    modifiers.add(PsiModifier.FINAL);
    ourFieldIncompatibleModifiers.put(PsiModifier.VOLATILE, modifiers);

    ourClassInitializerIncompatibleModifiers = new THashMap<String, Set<String>>(1);
    ourClassInitializerIncompatibleModifiers.put(PsiModifier.STATIC, Collections.<String>emptySet());

    ourConstructorNotAllowedModifiers = new THashSet<String>(6);
    ourConstructorNotAllowedModifiers.add(PsiModifier.ABSTRACT);
    ourConstructorNotAllowedModifiers.add(PsiModifier.STATIC);
    ourConstructorNotAllowedModifiers.add(PsiModifier.NATIVE);
    ourConstructorNotAllowedModifiers.add(PsiModifier.FINAL);
    ourConstructorNotAllowedModifiers.add(PsiModifier.STRICTFP);
    ourConstructorNotAllowedModifiers.add(PsiModifier.SYNCHRONIZED);
  }

  @Nullable
  public static String getIncompatibleModifier(String modifier,
                                               PsiModifierList modifierList,
                                               Map<String, Set<String>> incompatibleModifiersHash) {
    if (modifierList == null) return null;
    // modifier is always incompatible with itself
    PsiElement[] modifiers = modifierList.getChildren();
    int modifierCount = 0;
    for (PsiElement otherModifier : modifiers) {
      if (Comparing.equal(modifier, otherModifier.getText(), true)) modifierCount++;
    }
    if (modifierCount > 1) {
      return modifier;
    }

    Set<String> incompatibles = incompatibleModifiersHash.get(modifier);
    if (incompatibles == null) return null;
    for (final String incompatible : incompatibles) {
      if (modifierList.hasModifierProperty(incompatible)) {
        return incompatible;
      }
    }
    return null;
  }

  /**
   * make element protected/package local/public suggestion
   */
  static void registerAccessQuickFixAction(PsiMember refElement,
                                           PsiJavaCodeReferenceElement place,
                                           HighlightInfo errorResult,
                                           final PsiElement fileResolveScope) {
    if (refElement instanceof PsiCompiledElement) return;
    PsiModifierList modifierList = refElement.getModifierList();
    if (modifierList == null) return;

    PsiClass accessObjectClass = null;
    PsiElement scope = place;
    while (scope != null) {
      if (scope instanceof PsiClass) {
        accessObjectClass = (PsiClass)scope;
        break;
      }
      scope = scope.getParent();
    }

    PsiClass packageLocalClassInTheMiddle = getPackageLocalClassInTheMiddle(place);
    if (packageLocalClassInTheMiddle != null) {
      IntentionAction fix =
        QUICK_FIX_FACTORY.createModifierListFix(packageLocalClassInTheMiddle.getModifierList(), PsiModifier.PUBLIC, true, true);
      QuickFixAction.registerQuickFixAction(errorResult, fix);
      return;
    }

    try {
      PsiModifierList modifierListCopy = refElement.getManager().getElementFactory().createFieldFromText("int a;", null).getModifierList();
      modifierListCopy.setModifierProperty(PsiModifier.STATIC, modifierList.hasModifierProperty(PsiModifier.STATIC));
      int i = 0;
      if (refElement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        i = 1;
      }
      if (refElement.hasModifierProperty(PsiModifier.PROTECTED)) {
        i = 2;
      }
      String[] modifiers = new String[]{PsiModifier.PACKAGE_LOCAL, PsiModifier.PROTECTED, PsiModifier.PUBLIC};
      for (; i < modifiers.length; i++) {
        String modifier = modifiers[i];
        modifierListCopy.setModifierProperty(modifier, true);
        if (refElement.getManager().getResolveHelper()
          .isAccessible(refElement, modifierListCopy, place, accessObjectClass, fileResolveScope)) {
          IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(refElement.getModifierList(), modifier, true, true);
          QuickFixAction.registerQuickFixAction(errorResult, fix);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  private static PsiClass getPackageLocalClassInTheMiddle(PsiJavaCodeReferenceElement place) {
    if (place instanceof PsiReferenceExpression) {
      // check for package local classes in the middle
      PsiReferenceExpression expression = (PsiReferenceExpression)place;
      while (true) {
        PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiField) {
          PsiField field = (PsiField)resolved;
          PsiClass aClass = field.getContainingClass();
          if (aClass != null && aClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
              !aClass.getManager().arePackagesTheSame(aClass, place)) {

            return aClass;
          }
        }
        PsiExpression qualifier = expression.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression)) break;
        expression = (PsiReferenceExpression)qualifier;
      }
    }
    return null;
  }


  @Nullable
  static HighlightInfo checkInstanceOfApplicable(PsiInstanceOfExpression expression) {
    PsiExpression operand = expression.getOperand();
    PsiType checkType = expression.getCheckType().getType();
    PsiType operandType = operand.getType();
    if (operandType == null) return null;
    if (TypeConversionUtil.isPrimitiveAndNotNull(operandType) || TypeConversionUtil.isPrimitiveAndNotNull(checkType) ||
        !TypeConversionUtil.areTypesConvertible(operandType, checkType)) {
      String message = JavaErrorMessages.message("inconvertible.type.cast", formatType(operandType), formatType(checkType));
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, message);
    }
    return null;
  }


  @Nullable
  static HighlightInfo checkInconvertibleTypeCast(PsiTypeCastExpression expression) {
    PsiExpression operand = expression.getOperand();
    PsiType castType = expression.getCastType().getType();
    PsiType operandType = operand == null ? null : operand.getType();
    if (operandType != null && !TypeConversionUtil.areTypesConvertible(operandType, castType)) {
      String message = JavaErrorMessages.message("inconvertible.type.cast", formatType(operandType), formatType(castType));
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, message);
    }
    return null;
  }


  static HighlightInfo checkVariableExpected(PsiExpression expression) {
    PsiExpression lValue;
    if (expression instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)expression;
      lValue = assignment.getLExpression();
    }
    else if (PsiUtil.isIncrementDecrementOperation(expression)) {
      lValue = expression instanceof PsiPostfixExpression
               ? ((PsiPostfixExpression)expression).getOperand()
               : ((PsiPrefixExpression)expression).getOperand();
    }
    else {
      lValue = null;
    }
    HighlightInfo errorResult = null;
    if (lValue != null && !TypeConversionUtil.isLValue(lValue)) {
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, lValue, JavaErrorMessages.message("variable.expected"));
    }

    return errorResult;
  }


  @Nullable
  static HighlightInfo checkAssignmentOperatorApplicable(PsiAssignmentExpression assignment) {
    PsiJavaToken operationSign = assignment.getOperationSign();
    IElementType eqOpSign = operationSign.getTokenType();
    IElementType opSign = convertEQtoOperation(eqOpSign);
    if (opSign == null) return null;
    HighlightInfo errorResult = null;
    if (!TypeConversionUtil.isBinaryOperatorApplicable(opSign, assignment.getLExpression(), assignment.getRExpression(), true)) {
      String operatorText = operationSign.getText().substring(0, operationSign.getText().length() - 1);
      String message = JavaErrorMessages.message("binary.operator.not.applicable", operatorText,
                                                 formatType(assignment.getLExpression().getType()),
                                                 formatType(assignment.getRExpression().getType()));

      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, assignment, message);
    }
    return errorResult;
  }

  private static IElementType convertEQtoOperation(IElementType eqOpSign) {
    IElementType opSign = null;
    if (eqOpSign == JavaTokenType.ANDEQ) {
      opSign = JavaTokenType.AND;
    }
    else if (eqOpSign == JavaTokenType.ASTERISKEQ) {
      opSign = JavaTokenType.ASTERISK;
    }
    else if (eqOpSign == JavaTokenType.DIVEQ) {
      opSign = JavaTokenType.DIV;
    }
    else if (eqOpSign == JavaTokenType.GTGTEQ) {
      opSign = JavaTokenType.GTGT;
    }
    else if (eqOpSign == JavaTokenType.GTGTGTEQ) {
      opSign = JavaTokenType.GTGTGT;
    }
    else if (eqOpSign == JavaTokenType.LTLTEQ) {
      opSign = JavaTokenType.LTLT;
    }
    else if (eqOpSign == JavaTokenType.MINUSEQ) {
      opSign = JavaTokenType.MINUS;
    }
    else if (eqOpSign == JavaTokenType.OREQ) {
      opSign = JavaTokenType.OR;
    }
    else if (eqOpSign == JavaTokenType.PERCEQ) {
      opSign = JavaTokenType.PERC;
    }
    else if (eqOpSign == JavaTokenType.PLUSEQ) {
      opSign = JavaTokenType.PLUS;
    }
    else if (eqOpSign == JavaTokenType.XOREQ) {
      opSign = JavaTokenType.XOR;
    }
    return opSign;
  }


  @Nullable
  static HighlightInfo checkAssignmentCompatibleTypes(PsiAssignmentExpression assignment) {
    if (!"=".equals(assignment.getOperationSign().getText())) return null;
    PsiExpression lExpr = assignment.getLExpression();
    PsiExpression rExpr = assignment.getRExpression();
    if (rExpr == null) return null;
    PsiType lType = lExpr.getType();
    PsiType rType = rExpr.getType();
    if (rType == null) return null;

    HighlightInfo highlightInfo = checkAssignability(lType, rType, rExpr, assignment);
    if (highlightInfo != null) {
      PsiVariable leftVar = null;
      if (lExpr instanceof PsiReferenceExpression) {
        PsiElement element = ((PsiReferenceExpression)lExpr).resolve();
        if (element instanceof PsiVariable) {
          leftVar = (PsiVariable)element;
        }
      }
      if (leftVar != null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, new VariableTypeFix(leftVar, rType));
      }
    }
    return highlightInfo;
  }

  private static boolean isCastIntentionApplicable(PsiExpression expression, PsiType toType) {
    while (expression instanceof PsiTypeCastExpression || expression instanceof PsiParenthesizedExpression) {
      if (expression instanceof PsiTypeCastExpression) {
        expression = ((PsiTypeCastExpression)expression).getOperand();
      }
      if (expression instanceof PsiParenthesizedExpression) {
        expression = ((PsiParenthesizedExpression)expression).getExpression();
      }
    }
    if (expression == null) return false;
    PsiType rType = expression.getType();
    return rType != null && toType != null && TypeConversionUtil.areTypesConvertible(rType, toType);
  }


  @Nullable
  static HighlightInfo checkVariableInitializerType(PsiVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    // array initalizer checked in checkArrayInitializerApplicable
    if (initializer == null || initializer instanceof PsiArrayInitializerExpression) return null;
    PsiType lType = variable.getType();
    PsiType rType = initializer.getType();
    int start = variable.getTypeElement().getTextRange().getStartOffset();
    int end = variable.getTextRange().getEndOffset();
    HighlightInfo highlightInfo = checkAssignability(lType, rType, initializer, new TextRange(start, end));
    if (highlightInfo != null) {
      QuickFixAction.registerQuickFixAction(highlightInfo, new VariableTypeFix(variable, rType));
    }
    return highlightInfo;
  }

  @Nullable
  static HighlightInfo checkAssignability(PsiType lType, PsiType rType, PsiExpression expression, PsiElement elementToHighlight) {
    TextRange textRange = elementToHighlight.getTextRange();
    return checkAssignability(lType, rType, expression, textRange);
  }

  @Nullable
  public static HighlightInfo checkAssignability(@Nullable PsiType lType, @Nullable PsiType rType, @Nullable PsiExpression expression, TextRange textRange) {
    if (expression == null) {
      if (rType == null || lType == null || TypeConversionUtil.isAssignable(lType, rType)) return null;
    }
    else if (TypeConversionUtil.areTypesAssignmentCompatible(lType, expression)) {
      if (lType == null || rType == null) return null;
      return GenericsHighlightUtil.checkRawToGenericAssignment(lType, rType, expression);
    }
    if (rType == null) {
      rType = expression.getType();
    }
    HighlightInfo highlightInfo = createIncompatibleTypeHighlightInfo(lType, rType, textRange);
    if (rType != null && expression != null && isCastIntentionApplicable(expression, lType)) {
      QuickFixAction.registerQuickFixAction(highlightInfo, new AddTypeCastFix(lType, expression));
    }
    if (lType instanceof PsiClassType && expression != null) {
      QuickFixAction.registerQuickFixAction(highlightInfo, new WrapExpressionFix((PsiClassType)lType, expression));
    }
    ChangeNewOperatorTypeFix.register(highlightInfo, expression, lType);
    return highlightInfo;
  }


  @Nullable
  static HighlightInfo checkReturnStatementType(PsiReturnStatement statement) {
    PsiMethod method = null;
    PsiElement parent = statement.getParent();
    while (true) {
      if (parent instanceof PsiFile) break;
      if (parent instanceof PsiClassInitializer) break;
      if (parent instanceof PsiMethod) {
        method = (PsiMethod)parent;
        break;
      }
      parent = parent.getParent();
    }
    String description;
    int navigationShift = 0;
    HighlightInfo errorResult = null;
    if (method == null && !(parent instanceof JspFile)) {
      description = JavaErrorMessages.message("return.outside.method");
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.RETURN_OUTSIDE_METHOD, statement, description);
    }
    else {
      PsiType returnType = method != null ? method.getReturnType() : null/*JSP page returns void*/;
      boolean isMethodVoid = returnType == null || PsiType.VOID == returnType;
      PsiExpression returnValue = statement.getReturnValue();
      if (returnValue != null) {
        if (isMethodVoid) {
          description = JavaErrorMessages.message("return.from.void.method");
          errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, statement, description);
          IntentionAction fix = QUICK_FIX_FACTORY.createMethodReturnFix(method, returnValue.getType(), false);
          QuickFixAction.registerQuickFixAction(errorResult, fix);
        }
        else {
          PsiType valueType = returnValue.getType();
          errorResult = checkAssignability(returnType, valueType, returnValue, statement);
          if (errorResult != null) {
            IntentionAction fix = QUICK_FIX_FACTORY.createMethodReturnFix(method, returnValue.getType(), false);
            QuickFixAction.registerQuickFixAction(errorResult, fix);
          }
        }
        navigationShift = returnValue.getStartOffsetInParent();
      }
      else {
        if (!isMethodVoid) {
          description = JavaErrorMessages.message("missing.return.value");
          errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, statement, description);
          IntentionAction fix = QUICK_FIX_FACTORY.createMethodReturnFix(method, PsiType.VOID, false);
          QuickFixAction.registerQuickFixAction(errorResult, fix);
          navigationShift = PsiKeyword.RETURN.length();
        }
      }
    }
    if (errorResult != null) {
      errorResult.navigationShift = navigationShift;
    }
    return errorResult;
  }

  public static String getUnhandledExceptionsDescriptor(PsiClassType... unhandledExceptions) {
    StringBuilder exceptionsText = StringBuilderSpinAllocator.alloc();
    try {
      for (int i = 0; i < unhandledExceptions.length; i++) {
        PsiClassType unhandledException = unhandledExceptions[i];
        if (i > 0) exceptionsText.append(", ");
        exceptionsText.append(formatType(unhandledException));
      }

      return JavaErrorMessages.message("unhandled.exceptions", exceptionsText.toString(), unhandledExceptions.length);
    }
    finally {
      StringBuilderSpinAllocator.dispose(exceptionsText);
    }
  }


  @Nullable
  static HighlightInfo checkVariableAlreadyDefined(PsiVariable variable) {
    boolean isIncorrect = false;
    PsiIdentifier identifier = variable.getNameIdentifier();
    String name = identifier.getText();
    if (variable instanceof PsiLocalVariable ||
        variable instanceof PsiParameter && ((PsiParameter)variable).getDeclarationScope() instanceof PsiCatchSection ||
        variable instanceof PsiParameter && ((PsiParameter)variable).getDeclarationScope() instanceof PsiForeachStatement) {
      PsiElement scope = PsiTreeUtil.getParentOfType(variable, PsiFile.class, PsiMethod.class, PsiClassInitializer.class);
      VariablesNotProcessor proc = new VariablesNotProcessor(variable, false);
      PsiScopesUtil.treeWalkUp(proc, identifier, scope);
      if (proc.size() > 0) {
        isIncorrect = true;
      }
    }
    else if (variable instanceof PsiField) {
      PsiField field = (PsiField)variable;
      PsiClass aClass = field.getContainingClass();
      if (aClass == null) return null;
      PsiField fieldByName = aClass.findFieldByName(name, false);
      if (fieldByName != null && fieldByName != field) {
        isIncorrect = true;
      }
    }
    else {
      PsiElement scope = variable.getParent();
      PsiElement[] children = scope.getChildren();
      for (PsiElement child : children) {
        if (child instanceof PsiVariable) {
          if (child.equals(variable)) continue;
          if (name.equals(((PsiVariable)child).getName())) {
            isIncorrect = true;
            break;
          }
        }
      }
    }

    if (isIncorrect) {
      String description = JavaErrorMessages.message("variable.already.defined", name);
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, identifier, description);
      QuickFixAction.registerQuickFixAction(highlightInfo, new ReuseVariableDeclarationFix(variable, identifier));
      return highlightInfo;
    }
    return null;
  }

  @NotNull
  public static String formatClass(@NotNull PsiClass aClass) {
    return formatClass(aClass, true);
  }

  @NotNull
  public static String formatClass(@NotNull PsiClass aClass, boolean fqn) {
    return PsiFormatUtil.formatClass(aClass, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_ANONYMOUS_CLASS_VERBOSE | (fqn ? PsiFormatUtil.SHOW_FQ_NAME : 0));
  }

  @NotNull
  public static String formatMethod(@NotNull PsiMethod method) {
    return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS, PsiFormatUtil.SHOW_TYPE);
  }

  @NotNull
  public static String formatType(@Nullable PsiType type) {
    if (type == null) return PsiKeyword.NULL;
    return type.getInternalCanonicalText();
  }


  public static HighlightInfo checkUnhandledExceptions(PsiElement element, TextRange fixRange) {
    PsiClassType[] unhandledExceptions = ExceptionUtil.getUnhandledExceptions(element);
    HighlightInfo errorResult = null;
    if (unhandledExceptions.length > 0) {
      if (fixRange == null) {
        fixRange = element.getTextRange();
      }
      HighlightInfoType highlightType = getUnhandledExceptionHighlightType(element);
      if (highlightType == null) return null;
      errorResult = HighlightInfo.createHighlightInfo(highlightType, fixRange, getUnhandledExceptionsDescriptor(unhandledExceptions));
      QuickFixAction.registerQuickFixAction(errorResult, new AddExceptionToCatchFix());
      QuickFixAction.registerQuickFixAction(errorResult, new AddExceptionToThrowsFix(element));
      QuickFixAction.registerQuickFixAction(errorResult, new SurroundWithTryCatchAction(element));
      if (unhandledExceptions.length == 1) {
        QuickFixAction.registerQuickFixAction(errorResult, new GeneralizeCatchFix(element, unhandledExceptions[0]));
      }
    }
    return errorResult;
  }

  private static HighlightInfoType getUnhandledExceptionHighlightType(final PsiElement element) {
    if (!PsiUtil.isInJspFile(element)) {
      return HighlightInfoType.UNHANDLED_EXCEPTION;
    }
    PsiMethod targetMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (!(targetMethod instanceof JspHolderMethod)) return HighlightInfoType.UNHANDLED_EXCEPTION;
    // ignore JSP top level errors - it handled by UnhandledExceptionInJSP inspection
    return null;
  }


  @Nullable
  static HighlightInfo checkBreakOutsideLoop(PsiBreakStatement statement) {
    if (statement.getLabelIdentifier() == null) {
      if (new PsiMatcherImpl(statement).ancestor(PsiMatcherExpression.ENCLOSING_LOOP_OR_SWITCH).getElement() == null) {
        return HighlightInfo
          .createHighlightInfo(HighlightInfoType.ERROR, statement, JavaErrorMessages.message("break.outside.switch.or.loop"));
      }
    }
    else {
      // todo labeled
    }
    return null;
  }


  @Nullable
  static HighlightInfo checkContinueOutsideLoop(PsiContinueStatement statement) {
    if (statement.getLabelIdentifier() == null) {
      if (new PsiMatcherImpl(statement).ancestor(PsiMatcherExpression.ENCLOSING_LOOP).getElement() == null) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, statement, JavaErrorMessages.message("continue.outside.loop"));
      }
    }
    else {
      PsiStatement exitedStatement = statement.findContinuedStatement();
      if (exitedStatement == null) return null;
      if (!(exitedStatement instanceof PsiForStatement) && !(exitedStatement instanceof PsiWhileStatement) &&
          !(exitedStatement instanceof PsiDoWhileStatement) && !(exitedStatement instanceof PsiForeachStatement)) {
        String description = JavaErrorMessages.message("not.loop.label", statement.getLabelIdentifier().getText());
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, statement, description);
      }
    }
    return null;
  }


  static HighlightInfo checkIllegalModifierCombination(PsiKeyword keyword, PsiModifierList modifierList) {
    String modifier = keyword.getText();
    String incompatible = getIncompatibleModifier(modifier, modifierList);

    HighlightInfo highlightInfo = null;
    if (incompatible != null) {
      String message = JavaErrorMessages.message("incompatible.modifiers", modifier, incompatible);

      highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, keyword, message);

      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createModifierListFix(modifierList, modifier, false, false));
    }
    return highlightInfo;
  }

  @Nullable
  private static Map<String, Set<String>> getIncompatibleModifierMap(PsiModifierList modifierList) {
    PsiElement parent = modifierList.getParent();
    if (parent == null || PsiUtil.hasErrorElementChild(parent)) return null;
    return parent instanceof PsiClass
           ? ((PsiClass)parent).isInterface() ? ourInterfaceIncompatibleModifiers : ourClassIncompatibleModifiers
           : parent instanceof PsiMethod
             ? ourMethodIncompatibleModifiers
             : parent instanceof PsiVariable
               ? ourFieldIncompatibleModifiers
               : parent instanceof PsiClassInitializer ? ourClassInitializerIncompatibleModifiers : null;
  }

  @Nullable
  public static String getIncompatibleModifier(String modifier, PsiModifierList modifierList) {
    PsiElement parent = modifierList.getParent();
    if (parent == null || PsiUtil.hasErrorElementChild(parent)) return null;
    final Map<String, Set<String>> incompatibleModifierMap = getIncompatibleModifierMap(modifierList);
    if (incompatibleModifierMap == null) return null;
    return getIncompatibleModifier(modifier, modifierList, incompatibleModifierMap);
  }


  @Nullable
  public static HighlightInfo checkNotAllowedModifier(PsiKeyword keyword, PsiModifierList modifierList) {
    PsiElement modifierOwner = modifierList.getParent();
    if (modifierOwner == null) return null;
    if (PsiUtil.hasErrorElementChild(modifierOwner)) return null;
    String modifier = keyword.getText();
    final Map<String, Set<String>> incompatibleModifierMap = getIncompatibleModifierMap(modifierList);
    if (incompatibleModifierMap == null) return null;
    Set<String> incompatibles = incompatibleModifierMap.get(modifier);
    boolean isAllowed = true;
    PsiElement modifierOwnerParent = modifierOwner.getParent();
    if (modifierOwner instanceof PsiClass) {
      PsiClass aClass = (PsiClass)modifierOwner;
      if (aClass.isInterface()) {
        if (PsiModifier.STATIC.equals(modifier) || PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PROTECTED.equals(modifier) ||
            PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
          isAllowed = modifierOwnerParent instanceof PsiClass;
        }
      }
      else {
        if (PsiModifier.PUBLIC.equals(modifier)) {
          isAllowed = modifierOwnerParent instanceof PsiJavaFile || modifierOwnerParent instanceof PsiClass;
        }
        else if (PsiModifier.STATIC.equals(modifier) || PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PROTECTED.equals(modifier) ||
                 PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
          isAllowed = modifierOwnerParent instanceof PsiClass;
        }

        if (aClass.isEnum()) {
          isAllowed &= !(PsiModifier.FINAL.equals(modifier) || PsiModifier.ABSTRACT.equals(modifier));
        }
      }
    }
    else if (modifierOwner instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)modifierOwner;
      isAllowed = !(method.isConstructor() && ourConstructorNotAllowedModifiers.contains(modifier));
      if ((method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.PROTECTED)) && method.isConstructor() &&
          method.getContainingClass() != null && method.getContainingClass().isEnum()) {
        isAllowed = false;
      }

      if (PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PROTECTED.equals(modifier) || PsiModifier.TRANSIENT.equals(modifier) ||
          PsiModifier.STRICTFP.equals(modifier) || PsiModifier.SYNCHRONIZED.equals(modifier)) {
        boolean notInterface = modifierOwnerParent instanceof PsiClass && !((PsiClass)modifierOwnerParent).isInterface();
        isAllowed &= notInterface;
      }
    }
    else if (modifierOwner instanceof PsiField) {
      if (PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PROTECTED.equals(modifier) || PsiModifier.TRANSIENT.equals(modifier) ||
          PsiModifier.STRICTFP.equals(modifier) || PsiModifier.SYNCHRONIZED.equals(modifier)) {
        boolean isInterface = modifierOwnerParent instanceof PsiClass && !((PsiClass)modifierOwnerParent).isInterface();
        isAllowed = isInterface;
      }
    }
    else if (modifierOwner instanceof PsiClassInitializer) {
      isAllowed = PsiModifier.STATIC.equals(modifier);
    }
    else if (modifierOwner instanceof PsiLocalVariable || modifierOwner instanceof PsiParameter) {
      isAllowed = PsiModifier.FINAL.equals(modifier);
    }

    isAllowed &= incompatibles != null;
    if (!isAllowed) {
      String message = JavaErrorMessages.message("modifier.not.allowed", modifier);

      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, keyword, message);
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createModifierListFix(modifierList, modifier, false, false));
      return highlightInfo;
    }
    return null;
  }


  @Nullable
  static HighlightInfo checkLiteralExpressionParsingError(PsiLiteralExpression expression) {
    String error = expression.getParsingError();
    if (error != null) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, error);
    }
    return null;
  }


  @Nullable
  static HighlightInfo checkMustBeBoolean(PsiExpression expr) {
    PsiElement parent = expr.getParent();
    if (parent instanceof PsiIfStatement || parent instanceof PsiWhileStatement ||
        parent instanceof PsiForStatement && expr.equals(((PsiForStatement)parent).getCondition()) ||
        parent instanceof PsiDoWhileStatement && expr.equals(((PsiDoWhileStatement)parent).getCondition())) {
      if (expr.getNextSibling() instanceof PsiErrorElement) return null;

      PsiType type = expr.getType();
      if (!TypeConversionUtil.isBooleanType(type)) {
        return createIncompatibleTypeHighlightInfo(PsiType.BOOLEAN, type, expr.getTextRange());
      }
    }
    return null;
  }


  @Nullable
  static HighlightInfo checkExceptionThrownInTry(PsiParameter parameter) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (!(declarationScope instanceof PsiCatchSection)) return null;
    PsiTryStatement statement = ((PsiCatchSection)declarationScope).getTryStatement();
    PsiClassType[] classes = ExceptionUtil.collectUnhandledExceptions(statement.getTryBlock(), statement.getTryBlock());
    if (classes == null) classes = PsiClassType.EMPTY_ARRAY;

    PsiType caughtType = parameter.getType();
    if (!(caughtType instanceof PsiClassType)) return null;
    if (ExceptionUtil.isUncheckedExceptionOrSuperclass((PsiClassType)caughtType)) return null;

    for (PsiClassType exceptionType : classes) {
      if (exceptionType.isAssignableFrom(caughtType) || caughtType.isAssignableFrom(exceptionType)) return null;
    }

    String description = JavaErrorMessages.message("exception.never.thrown.try", formatType(caughtType));
    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, parameter, description);

    QuickFixAction.registerQuickFixAction(errorResult, new DeleteCatchFix(parameter));
    return errorResult;
  }


  @Nullable
  static HighlightInfo checkNotAStatement(PsiStatement statement) {
    if (!PsiUtil.isStatement(statement)) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, statement, JavaErrorMessages.message("not.a.statement"));
    }
    return null;
  }


  public static HighlightInfo checkSwitchSelectorType(PsiSwitchStatement statement) {
    PsiExpression expression = statement.getExpression();
    HighlightInfo errorResult = null;
    if (expression != null && expression.getType() != null) {
      PsiType type = expression.getType();
      if (!isValidTypeForSwitchSelector(type)) {
        String message =
          JavaErrorMessages.message("incompatible.types", JavaErrorMessages.message("valid.switch.selector.types"), formatType(type));
        errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, message);
        if (PsiType.LONG == type || PsiType.FLOAT == type || PsiType.DOUBLE == type) {
          QuickFixAction.registerQuickFixAction(errorResult, new AddTypeCastFix(PsiType.INT, expression));
        }
      }
    }
    return errorResult;
  }

  private static boolean isValidTypeForSwitchSelector(PsiType type) {
    if (TypeConversionUtil.getTypeRank(type) <= TypeConversionUtil.INT_RANK) return true;
    if (type instanceof PsiClassType) {
      PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass == null) return false;
      if (psiClass.isEnum()) {
        return true;
      }
    }
    return false;
  }


  @Nullable
  static HighlightInfo checkBinaryOperatorApplicable(PsiBinaryExpression expression) {
    PsiExpression lOperand = expression.getLOperand();
    PsiExpression rOperand = expression.getROperand();
    PsiJavaToken operationSign = expression.getOperationSign();
    if (!TypeConversionUtil.isBinaryOperatorApplicable(operationSign.getTokenType(), lOperand, rOperand, false)) {
      String message = JavaErrorMessages
        .message("binary.operator.not.applicable", operationSign.getText(), formatType(lOperand.getType()), formatType(rOperand.getType()));
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, message);
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkUnaryOperatorApplicable(PsiJavaToken token, PsiExpression expression) {
    if (token != null && expression != null && !TypeConversionUtil.isUnaryOperatorApplicable(token, expression)) {
      PsiType type = expression.getType();
      if (type == null) return null;
      String message = JavaErrorMessages.message("unary.operator.not.applicable", token.getText(), formatType(type));

      PsiElement parentExpr = token.getParent();
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, parentExpr, message);
      if (parentExpr instanceof PsiPrefixExpression && token.getTokenType() == JavaTokenType.EXCL) {
        QuickFixAction.registerQuickFixAction(highlightInfo, new NegationBroadScopeFix((PsiPrefixExpression)parentExpr));
      }
      return highlightInfo;
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkThisOrSuperExpressionInIllegalContext(PsiExpression expr,
                                                                         @Nullable PsiJavaCodeReferenceElement qualifier) {
    if (expr instanceof PsiSuperExpression && !(expr.getParent() instanceof PsiReferenceExpression)) {
      // like in 'Object o = super;'
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expr.getTextRange().getEndOffset(),
                                               expr.getTextRange().getEndOffset() + 1,
                                               JavaErrorMessages.message("dot.expected.after.super.or.this"));
    }
    PsiClass aClass = qualifier == null ? PsiTreeUtil.getParentOfType(expr, PsiClass.class) : (PsiClass)qualifier.resolve();
    if (aClass == null) return null;
    if (qualifier != null && aClass.isInterface()) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, qualifier, HighlightClassUtil.CLASS_EXPECTED);
    }
    if (!HighlightClassUtil.hasEnclosingInstanceInScope(aClass, expr, false)) {
      return HighlightClassUtil.reportIllegalEnclosingUsage(expr, null, aClass, expr);
    }

    return null;

  }

  static String buildProblemWithStaticDescription(PsiElement refElement) {
    @NonNls String key = "";
    if (refElement instanceof PsiVariable) {
      key = "non.static.variable.referenced.from.static.context";
    }
    else if (refElement instanceof PsiMethod) {
      key = "non.static.method.referenced.from.static.context";
    }
    else if (refElement instanceof PsiClass) {
      key = "non.static.class.referenced.from.static.context";
    }
    else {
      LOG.error("???" + refElement);
    }

    return JavaErrorMessages.message(key, HighlightMessageUtil.getSymbolName(refElement, PsiSubstitutor.EMPTY));
  }

  static void registerStaticProblemQuickFixAction(@NotNull PsiElement refElement,
                                                  HighlightInfo errorResult, PsiJavaCodeReferenceElement place) {
    PsiModifierList modifierList = null;
    if (refElement instanceof PsiModifierListOwner) {
      modifierList = ((PsiModifierListOwner)refElement).getModifierList();
    }
    if (modifierList != null) {
      QuickFixAction
        .registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createModifierListFix(modifierList, PsiModifier.STATIC, true, false));
    }
    // make context non static
    PsiModifierListOwner staticParent = PsiUtil.getEnclosingStaticElement(place, null);
    if (staticParent != null) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createModifierListFix(staticParent.getModifierList(),
                                                                                                 PsiModifier.STATIC, false, false));
    }
  }

  static String buildProblemWithAccessDescription(PsiJavaCodeReferenceElement reference, JavaResolveResult result) {
    PsiModifierListOwner refElement = (PsiModifierListOwner)result.getElement();
    String symbolName = HighlightMessageUtil.getSymbolName(refElement, result.getSubstitutor());

    if (refElement.hasModifierProperty(PsiModifier.PRIVATE)) {
      String containerName = HighlightMessageUtil.getSymbolName(refElement.getParent(), result.getSubstitutor());
      return JavaErrorMessages.message("private.symbol", symbolName, containerName);
    }
    else {
      if (refElement.hasModifierProperty(PsiModifier.PROTECTED)) {
        String containerName = HighlightMessageUtil.getSymbolName(refElement.getParent(), result.getSubstitutor());
        return JavaErrorMessages.message("protected.symbol", symbolName, containerName);
      }
      else {
        PsiClass packageLocalClass = getPackageLocalClassInTheMiddle(reference);
        if (packageLocalClass != null) {
          refElement = packageLocalClass;
          symbolName = HighlightMessageUtil.getSymbolName(refElement, result.getSubstitutor());
        }
        if (refElement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) || packageLocalClass != null) {
          String containerName = HighlightMessageUtil.getSymbolName(refElement.getParent(), result.getSubstitutor());
          return JavaErrorMessages.message("package.local.symbol", symbolName, containerName);
        }
        else {
          String containerName = HighlightMessageUtil.getSymbolName(
            refElement instanceof PsiTypeParameter ? refElement.getParent().getParent() : refElement.getParent(), result.getSubstitutor());
          return JavaErrorMessages.message("visibility.access.problem", symbolName, containerName);
        }
      }
    }
  }

  static String buildArgTypesList(PsiExpressionList list) {
    StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("(");
      PsiExpression[] args = list.getExpressions();
      for (int i = 0; i < args.length; i++) {
        if (i > 0) {
          builder.append(", ");
        }
        PsiType argType = args[i].getType();
        builder.append(argType != null ? formatType(argType) : "?");
      }
      builder.append(")");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }


  @Nullable
  static HighlightInfo checkValidArrayAccessExpression(PsiExpression arrayExpression, PsiExpression indexExpression) {
    PsiType arrayExpressionType = arrayExpression == null ? null : arrayExpression.getType();
    if (arrayExpressionType != null && !(arrayExpressionType instanceof PsiArrayType)) {
      String description = JavaErrorMessages.message("array.type.expected", formatType(arrayExpressionType));
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, arrayExpression, description);
    }
    return checkAssignability(PsiType.INT, indexExpression.getType(), indexExpression, indexExpression);
  }


  @Nullable
  public static HighlightInfo checkCatchParameterIsThrowable(PsiParameter parameter) {
    if (parameter.getDeclarationScope() instanceof PsiCatchSection) {
      PsiType type = parameter.getType();
      return checkMustBeThrowable(type, parameter, true);
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkArrayInitalizerCompatibleTypes(PsiExpression initializer) {
    if (!(initializer.getParent() instanceof PsiArrayInitializerExpression)) return null;
    PsiElement element = initializer.getParent();
    int dimensions = 0;
    while (element instanceof PsiArrayInitializerExpression) {
      element = element.getParent();
      dimensions++;
    }
    PsiType elementType;
    if (element instanceof PsiVariable) {
      elementType = ((PsiVariable)element).getType();
    }
    else if (element instanceof PsiNewExpression) {
      elementType = ((PsiNewExpression)element).getType();
    }
    else {
      // todo cdr illegal ?
      return null;
    }

    if (elementType == null) return null;
    PsiType type = elementType;
    for (; dimensions > 0; dimensions--) {
      if (!(type instanceof PsiArrayType)) break;
      type = ((PsiArrayType)type).getComponentType();
      if (type == null) break;
    }
    if (dimensions != 0) {
      return null;
      // we should get error when visit parent
    }

    if (type != null) {
      // compute initializer type based on initializer text
      PsiType initializerType =
        getInitializerType(initializer, type instanceof PsiArrayType ? ((PsiArrayType)type).getComponentType() : type);
      if (initializerType instanceof PsiArrayType && type instanceof PsiArrayType) return null;
      // do not use PsiArrayInitializerExpression.getType() for computing expression type
      PsiExpression expression = initializer instanceof PsiArrayInitializerExpression ? null : initializer;
      return checkAssignability(type, initializerType, expression, initializer);
    }
    return null;
  }

  @Nullable
  private static PsiType getInitializerType(PsiExpression expression, PsiType compTypeForEmptyInitializer) {
    if (expression instanceof PsiArrayInitializerExpression) {
      PsiExpression[] initializers = ((PsiArrayInitializerExpression)expression).getInitializers();
      PsiType compType;
      if (initializers.length == 0) {
        compType = compTypeForEmptyInitializer;
      }
      else {
        PsiType componentType =
          compTypeForEmptyInitializer instanceof PsiArrayType ? ((PsiArrayType)compTypeForEmptyInitializer).getComponentType() : null;
        compType = getInitializerType(initializers[0], componentType);
      }
      return compType == null ? null : compType.createArrayType();
    }
    return expression.getType();
  }


  @Nullable
  public static HighlightInfo checkExpressionRequired(PsiReferenceExpression expression) {
    if (expression.getNextSibling() instanceof PsiErrorElement) return null;
    PsiElement resolved = expression.advancedResolve(true).getElement();
    if (resolved == null) return null;
    PsiElement parent = expression.getParent();
    // String.class or String() are both correct
    if (parent instanceof PsiReferenceExpression || parent instanceof PsiMethodCallExpression) return null;
    if (resolved instanceof PsiVariable) return null;
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, JavaErrorMessages.message("expression.expected"));
  }


  @Nullable
  public static HighlightInfo checkArrayInitializerApplicable(PsiArrayInitializerExpression expression) {
    /*
    JLS 10.6 Array Initializers
    An array initializer may be specified in a declaration, or as part of an array creation expression
    */
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)parent;
      if (variable.getType() instanceof PsiArrayType) return null;
    }
    else if (parent instanceof PsiNewExpression) {
      return null;
    }
    else if (parent instanceof PsiArrayInitializerExpression) {
      return null;
    }
    HighlightInfo info =
      HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, JavaErrorMessages.message("expression.expected"));
    QuickFixAction.registerQuickFixAction(info, new AddNewArrayExpressionFix(expression));

    return info;
  }


  @Nullable
  public static HighlightInfo checkCaseStatement(PsiSwitchLabelStatement statement) {
    PsiSwitchStatement switchStatement = statement.getEnclosingSwitchStatement();
    if (switchStatement == null) {
      return HighlightInfo
        .createHighlightInfo(HighlightInfoType.ERROR, statement, JavaErrorMessages.message("case.statement.outside.switch"));
    }
    if (switchStatement.getBody() == null) return null;
    PsiExpression switchExpression = switchStatement.getExpression();
    PsiType switchType = switchExpression == null ? PsiType.INT : switchExpression.getType();
    // check constant expression
    PsiExpression caseValue = statement.getCaseValue();

    // Every case constant expression associated with a switch statement must be assignable (?5.2) to the type of the switch Expression.
    if (caseValue != null && switchExpression != null) {
      HighlightInfo highlightInfo = checkAssignability(switchType, caseValue.getType(), caseValue, caseValue);
      if (highlightInfo != null) return highlightInfo;
    }
    Object value = null;

    boolean isEnumSwitch = false;
    if (!statement.isDefaultCase() && caseValue != null) {
      if (caseValue instanceof PsiReferenceExpression) {
        PsiElement element = ((PsiReferenceExpression)caseValue).resolve();
        if (element instanceof PsiEnumConstant) {
          isEnumSwitch = true;
          value = ((PsiEnumConstant)element).getName();
          if (!(((PsiReferenceExpression)caseValue).getQualifier() == null)) {
            String message = JavaErrorMessages.message("qualified.enum.constant.in.switch");
            return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, caseValue, message);
          }
        }
      }
      if (!isEnumSwitch) {
        value = ConstantExpressionUtil.computeCastTo(caseValue, switchType);
      }
      if (value == null) {
        return HighlightInfo
          .createHighlightInfo(HighlightInfoType.ERROR, caseValue, JavaErrorMessages.message("constant.expression.required"));
      }
    }

    // check duplicate
    PsiStatement[] statements = switchStatement.getBody().getStatements();
    for (PsiStatement st : statements) {
      if (st == statement) continue;
      if (!(st instanceof PsiSwitchLabelStatement)) continue;
      PsiSwitchLabelStatement labelStatement = (PsiSwitchLabelStatement)st;
      if (labelStatement.isDefaultCase() != statement.isDefaultCase()) continue;
      PsiExpression caseExpr = labelStatement.getCaseValue();
      if (isEnumSwitch && caseExpr instanceof PsiReferenceExpression) {
        PsiElement element = ((PsiReferenceExpression)caseExpr).resolve();
        if (!(element instanceof PsiEnumConstant && Comparing.equal(((PsiEnumConstant)element).getName(), value))) continue;
      }
      else {
        // not assignable error already caught
        if (!TypeConversionUtil.areTypesAssignmentCompatible(switchType, caseExpr)) continue;
        if (!Comparing.equal(ConstantExpressionUtil.computeCastTo(caseExpr, switchType), value)) continue;
      }
      String description = statement.isDefaultCase()
                           ? JavaErrorMessages.message("duplicate.default.switch.label")
                           : JavaErrorMessages.message("duplicate.switch.label", value);
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, value == null ? statement : caseValue, description);
    }

    // must be followed with colon
    PsiElement lastChild = statement.getLastChild();
    while (lastChild instanceof PsiComment || lastChild instanceof PsiWhiteSpace) {
      lastChild = lastChild.getPrevSibling();
    }
    if (!(lastChild instanceof PsiJavaToken && ((PsiJavaToken)lastChild).getTokenType() == JavaTokenType.COLON)) {
      int start = statement.getTextRange().getEndOffset();
      int end = statement.getTextRange().getEndOffset() + 1;
      HighlightInfo highlightInfo = HighlightInfo
        .createHighlightInfo(HighlightInfoType.ERROR, start, end, JavaErrorMessages.message("switch.colon.expected.after.case.label"));
      char[] chars = statement.getContainingFile().textToCharArray();
      highlightInfo.isAfterEndOfLine = end >= chars.length || chars[start] == '\n' || chars[start] == '\r';
      return highlightInfo;
    }
    return null;
  }


  /**
   * see JLS 8.3.2.3
   */
  @Nullable
  public static HighlightInfo checkIllegalForwardReferenceToField(PsiReferenceExpression expression, PsiField referencedField) {
    PsiClass containingClass = referencedField.getContainingClass();
    if (containingClass == null) return null;
    if (expression.getContainingFile() != referencedField.getContainingFile()) return null;
    if (expression.getTextRange().getStartOffset() >= referencedField.getTextRange().getEndOffset()) return null;
    // only simple reference can be illegal
    if (expression.getQualifierExpression() != null) return null;
    PsiField initField = findEnclosingFieldInitializer(expression);
    PsiClassInitializer classInitializer = findParentClassInitializer(expression);
    if (initField == null && classInitializer == null) return null;
    // instance initializers may access static fields
    boolean isStaticClassInitializer = classInitializer != null && classInitializer.hasModifierProperty(PsiModifier.STATIC);
    boolean isStaticInitField = initField != null && initField.hasModifierProperty(PsiModifier.STATIC);
    boolean inStaticContext = isStaticInitField || isStaticClassInitializer;
    if (!inStaticContext && referencedField.hasModifierProperty(PsiModifier.STATIC)) return null;
    if (PsiUtil.isOnAssignmentLeftHand(expression) && !PsiUtil.isAccessedForReading(expression)) return null;
    if (!containingClass.getManager().areElementsEquivalent(containingClass, PsiTreeUtil.getParentOfType(expression, PsiClass.class))) {
      return null;
    }
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, JavaErrorMessages.message("illegal.forward.reference"));
  }

  /**
   * @return field that has initializer with this element as subexpression or null if not found
   */
  @Nullable
  static PsiField findEnclosingFieldInitializer(PsiElement element) {
    while (element != null) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiField) {
        PsiField field = (PsiField)parent;
        if (element == field.getInitializer()) return field;
        if (field instanceof PsiEnumConstant && element == ((PsiEnumConstant)field).getArgumentList()) return field;
      }
      if (element instanceof PsiClass || element instanceof PsiMethod) return null;
      element = parent;
    }
    return null;
  }

  @Nullable
  private static PsiClassInitializer findParentClassInitializer(PsiElement element) {
    while (element != null) {
      if (element instanceof PsiClassInitializer) return (PsiClassInitializer)element;
      if (element instanceof PsiClass || element instanceof PsiMethod) return null;
      element = element.getParent();
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkIllegalType(PsiTypeElement typeElement) {
    if (typeElement == null || typeElement.getParent() instanceof PsiTypeElement) return null;

    final PsiElement parent = typeElement.getParent();
    if (PsiTreeUtil.getParentOfType(parent, PsiDocComment.class) != null) {
      return null;
    }

    PsiType type = typeElement.getType();
    PsiType componentType = type.getDeepComponentType();
    if (componentType instanceof PsiClassType) {
      PsiClass aClass = PsiUtil.resolveClassInType(componentType);
      if (aClass == null) {
        String canonicalText = type.getCanonicalText();
        String description = JavaErrorMessages.message("unknown.class", canonicalText);
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeElement, description);
      }
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkIllegalVoidType(PsiKeyword type) {
    if (!PsiKeyword.VOID.equals(type.getText())) return null;

    PsiElement parent = type.getParent();
    if (parent instanceof PsiTypeElement) {
      PsiElement typeOwner = parent.getParent();
      if (typeOwner instanceof PsiMethod) {
        if (((PsiMethod)typeOwner).getReturnTypeElement() == parent) return null;
      }
      else if (typeOwner instanceof PsiClassObjectAccessExpression &&
               TypeConversionUtil.isVoidType(((PsiClassObjectAccessExpression)typeOwner).getOperand().getType())) {
        // like in Class c = void.class;
        return null;
      }
      else if (typeOwner != null && PsiUtil.hasErrorElementChild(typeOwner)) {
        // do not highlight incomplete declarations
        return null;
      }
      else if (typeOwner instanceof PsiCodeFragment) {
        if (typeOwner.getUserData(PsiUtil.VALID_VOID_TYPE_IN_CODE_FRAGMENT) != null) return null;
      }
    }

    String description = JavaErrorMessages.message("illegal.type.void");
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, type, description);
  }

  @Nullable
  public static HighlightInfo checkMemberReferencedBeforeConstructorCalled(PsiElement expression) {
    if (expression.getParent() instanceof PsiJavaCodeReferenceElement) return null;
    PsiClass referencedClass;
    String resolvedName;
    PsiType type;
    if (expression instanceof PsiJavaCodeReferenceElement) {
      PsiElement resolved = ((PsiJavaCodeReferenceElement)expression).advancedResolve(true).getElement();
      // redirected ctr
      if (PsiKeyword.THIS.equals(((PsiJavaCodeReferenceElement)expression).getReferenceName())
          && resolved instanceof PsiMethod
          && ((PsiMethod)resolved).isConstructor()) return null;
      PsiElement qualifier = ((PsiJavaCodeReferenceElement)expression).getQualifier();
      type = qualifier instanceof PsiExpression ? ((PsiExpression)qualifier).getType() : null;
      referencedClass = PsiUtil.resolveClassInType(type);

      boolean isSuperCall = isSuperMethodCall(expression.getParent());
      if (resolved == null && isSuperCall) {
        if (qualifier instanceof PsiReferenceExpression) {
          resolved = ((PsiReferenceExpression)qualifier).resolve();
          expression = qualifier;
          type = ((PsiReferenceExpression)qualifier).getType();
          referencedClass = PsiUtil.resolveClassInType(type);
        }
        else if (qualifier instanceof PsiThisExpression || qualifier == null) {
          resolved = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiMember.class);
          expression = qualifier == null ? expression : qualifier;
          if (resolved instanceof PsiMethod) {
            referencedClass = ((PsiMethod)resolved).getContainingClass();
          }
        }
      }
      if (resolved instanceof PsiField) {
        PsiField referencedField = (PsiField)resolved;
        if (referencedField.hasModifierProperty(PsiModifier.STATIC)) return null;
        resolvedName = PsiFormatUtil.formatVariable(referencedField, PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_NAME, PsiSubstitutor.EMPTY);
        referencedClass = referencedField.getContainingClass();
      }
      else if (resolved instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)resolved;
        if (method.hasModifierProperty(PsiModifier.STATIC)) return null;
        PsiElement nameElement = expression instanceof PsiThisExpression ? expression : ((PsiJavaCodeReferenceElement)expression).getReferenceNameElement();
        String name = nameElement == null ? null : nameElement.getText();
        if (isSuperCall) {
          if (referencedClass == null) return null;
          if (qualifier == null) {
            PsiClass superClass = referencedClass.getSuperClass();
            if (superClass != null
                && PsiUtil.isInnerClass(superClass)
                && InheritanceUtil.isInheritorOrSelf(referencedClass, superClass.getContainingClass(), true)) {
              // by default super() is considered this. - qualified
              resolvedName = PsiKeyword.THIS;
            }
            else {
              return null;
            }
          }
          else {
            resolvedName = qualifier.getText();
          }
        }
        else if (PsiKeyword.THIS.equals(name))  {
          resolvedName = PsiKeyword.THIS;
        }
        else {
          resolvedName = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_NAME, 0);
          if (referencedClass == null) referencedClass = method.getContainingClass();
        }
      }
      else if (resolved instanceof PsiClass) {
        PsiClass aClass = (PsiClass)resolved;
        if (aClass.hasModifierProperty(PsiModifier.STATIC)) return null;
        referencedClass = aClass.getContainingClass();
        if (referencedClass == null) return null;
        resolvedName = PsiFormatUtil.formatClass(aClass, PsiFormatUtil.SHOW_NAME);
      }
      else {
        return null;
      }
    }
    else if (expression instanceof PsiThisExpression) {
      type = ((PsiThisExpression)expression).getType();
      referencedClass = PsiUtil.resolveClassInType(type);
      resolvedName = referencedClass == null
                     ? null
                     : PsiFormatUtil.formatClass(referencedClass, PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_NAME);
    }
    else {
      return null;
    }
    if (referencedClass == null) return null;
    return checkReferenceToOurInstanceInsideThisOrSuper(expression, referencedClass, resolvedName);
  }

  private static HighlightInfo checkReferenceToOurInstanceInsideThisOrSuper(final PsiElement expression,
                                                                            final PsiClass referencedClass,
                                                                            final String resolvedName) {
    if (PsiTreeUtil.getParentOfType(expression, PsiReferenceParameterList.class) != null) return null;
    PsiElement element = expression.getParent();
    while (element != null) {
      // check if expression inside super()/this() call
      if (isSuperOrThisMethodCall(element)) {
        PsiElement parentClass = new PsiMatcherImpl(element)
          .parent(PsiMatcherImpl.hasClass(PsiExpressionStatement.class))
          .parent(PsiMatcherImpl.hasClass(PsiCodeBlock.class))
          .parent(PsiMatcherImpl.hasClass(PsiMethod.class))
          .dot(PsiMatcherImpl.isConstructor(true))
          .parent(PsiMatcherImpl.hasClass(PsiClass.class))
          .getElement();
        if (parentClass == null) {
          return null;
        }

        // only this class/superclasses instance methods are not allowed to call
        PsiClass aClass = (PsiClass)parentClass;
        if (PsiUtil.isInnerClass(aClass) && referencedClass == aClass.getContainingClass()) return null;
        // field or method should be declared in this class or super
        if (!InheritanceUtil.isInheritorOrSelf(aClass, referencedClass, true)) return null;
        // and point to our instance
        if (expression instanceof PsiReferenceExpression &&
            !thisOrSuperReference(((PsiReferenceExpression)expression).getQualifierExpression(), aClass)) {
          return null;
        }
        return createMemberReferencedError(resolvedName, expression.getTextRange());
      }
      element = element.getParent();
    }
    return null;
  }

  private static HighlightInfo createMemberReferencedError(@NonNls final String resolvedName, TextRange textRange) {
    String description = JavaErrorMessages.message("member.referenced.before.constructor.called", resolvedName);
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, description);
  }

  public static HighlightInfo checkImplicitThisReferenceBeforeSuper(PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) return null;
    PsiClass superClass = aClass.getSuperClass();
    if (superClass == null || !PsiUtil.isInnerClass(superClass)) return null;
    PsiClass outerClass = superClass.getContainingClass();
    if (!InheritanceUtil.isInheritorOrSelf(aClass, outerClass, true)) {
      return null;
    }
    // 'this' can be used as an (implicit) super() qualifier
    PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      TextRange range = com.intellij.codeInsight.ClassUtil.getClassDeclarationTextRange(aClass);
      return createMemberReferencedError(aClass.getName()+".this", range);
    }
    for (PsiMethod constructor : constructors) {
      if (!isSuperCalled(constructor)) {
        return createMemberReferencedError(aClass.getName()+".this", getMethodDeclarationTextRange(constructor));
      }
    }
    return null;
  }

  private static boolean isSuperCalled(final PsiMethod constructor) {
    final PsiCodeBlock body = constructor.getBody();
    if (body == null) return false;
    final PsiStatement[] statements = body.getStatements();
    if (statements.length == 0) return false;
    final PsiStatement statement = statements[0];
    final PsiElement element = new PsiMatcherImpl(statement)
        .dot(PsiMatcherImpl.hasClass(PsiExpressionStatement.class))
        .firstChild(PsiMatcherImpl.hasClass(PsiMethodCallExpression.class))
        .firstChild(PsiMatcherImpl.hasClass(PsiReferenceExpression.class))
        .firstChild(PsiMatcherImpl.hasClass(PsiKeyword.class))
        .dot(PsiMatcherImpl.hasText(PsiKeyword.SUPER))
        .getElement();
    return element != null;
  }

  public static boolean isSuperOrThisMethodCall(PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression)) return false;
    PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)element).getMethodExpression();
    String name = methodExpression.getReferenceName();
    return PsiKeyword.SUPER.equals(name) || PsiKeyword.THIS.equals(name);
  }
  public static boolean isSuperMethodCall(PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression)) return false;
    PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)element).getMethodExpression();
    String name = methodExpression.getReferenceName();
    return PsiKeyword.SUPER.equals(name);
  }

  private static boolean thisOrSuperReference(PsiExpression qualifierExpression, PsiClass aClass) {
    if (qualifierExpression == null) return true;
    PsiJavaCodeReferenceElement qualifier;
    if (qualifierExpression instanceof PsiThisExpression) {
      qualifier = ((PsiThisExpression)qualifierExpression).getQualifier();
    }
    else if (qualifierExpression instanceof PsiSuperExpression) {
      qualifier = ((PsiSuperExpression)qualifierExpression).getQualifier();
    }
    else {
      return false;
    }
    if (qualifier == null) return true;
    PsiElement resolved = qualifier.resolve();
    return resolved instanceof PsiClass && InheritanceUtil.isInheritorOrSelf(aClass, (PsiClass)resolved, true);
  }


  @Nullable
  public static HighlightInfo checkLabelWithoutStatement(PsiLabeledStatement statement) {
    if (statement.getStatement() == null) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, statement, JavaErrorMessages.message("label.without.statement"));
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkLabelAlreadyInUse(PsiLabeledStatement statement) {
    PsiIdentifier identifier = statement.getLabelIdentifier();
    String text = identifier.getText();
    PsiElement element = statement;
    while (element != null) {
      if (element instanceof PsiMethod || element instanceof PsiClass) break;
      if (element instanceof PsiLabeledStatement && element != statement &&
          Comparing.equal(((PsiLabeledStatement)element).getLabelIdentifier().getText(), text)) {
        String description = JavaErrorMessages.message("duplicate.label", text);
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, identifier, description);
      }
      element = element.getParent();
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkUnclosedComment(PsiComment comment) {
    if (!(comment instanceof PsiDocComment) && !(comment.getTokenType() == JavaTokenType.C_STYLE_COMMENT)) return null;
    if (!comment.getText().endsWith("*/")) {
      int start = comment.getTextRange().getEndOffset() - 1;
      int end = start + 1;
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, start, end, JavaErrorMessages.message("nonterminated.comment"));
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkExceptionAlreadyCaught(PsiJavaCodeReferenceElement element, PsiElement resolved) {
    if (!(resolved instanceof PsiClass)) return null;
    PsiClass catchClass = (PsiClass)resolved;
    if (!(element.getParent() instanceof PsiTypeElement)) return null;
    PsiElement catchParameter = element.getParent().getParent();
    if (!(catchParameter instanceof PsiParameter) || !(((PsiParameter)catchParameter).getDeclarationScope() instanceof PsiCatchSection)) {
      return null;
    }
    PsiCatchSection catchSection = (PsiCatchSection)((PsiParameter)catchParameter).getDeclarationScope();
    PsiTryStatement statement = catchSection.getTryStatement();
    PsiCatchSection[] catchSections = statement.getCatchSections();
    int i = ArrayUtil.find(catchSections, catchSection);
    for (i--; i >= 0; i--) {
      PsiCatchSection section = catchSections[i];
      PsiType type = section.getCatchType();
      PsiClass upCatchClass = PsiUtil.resolveClassInType(type);
      if (upCatchClass == null) continue;
      if (InheritanceUtil.isInheritorOrSelf(catchClass, upCatchClass, true)) {
        String description = JavaErrorMessages
          .message("exception.already.caught", PsiFormatUtil.formatClass(catchClass, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_FQ_NAME));
        HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, element, description);
        QuickFixAction.registerQuickFixAction(highlightInfo, new MoveCatchUpFix(catchSection, section));
        QuickFixAction.registerQuickFixAction(highlightInfo, new DeleteCatchFix((PsiParameter)catchParameter));
        return highlightInfo;
      }
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkTernaryOperatorConditionIsBoolean(PsiExpression expression) {
    if (expression.getParent() instanceof PsiConditionalExpression &&
        ((PsiConditionalExpression)expression.getParent()).getCondition() == expression && expression.getType() != null &&
        !TypeConversionUtil.isBooleanType(expression.getType())) {
      PsiType foundType = expression.getType();
      return createIncompatibleTypeHighlightInfo(PsiType.BOOLEAN, foundType, expression.getTextRange());
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkStatementPrependedWithCaseInsideSwitch(PsiStatement statement) {
    if (!(statement instanceof PsiSwitchLabelStatement) && statement.getParent() instanceof PsiCodeBlock &&
        statement.getParent().getParent() instanceof PsiSwitchStatement &&
        ((PsiCodeBlock)statement.getParent()).getStatements().length != 0 &&
        statement == ((PsiCodeBlock)statement.getParent()).getStatements()[0]) {
      return HighlightInfo
        .createHighlightInfo(HighlightInfoType.ERROR, statement, JavaErrorMessages.message("statement.must.be.prepended.with.case.label"));
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkAssertOperatorTypes(PsiExpression expression) {
    if (!(expression.getParent() instanceof PsiAssertStatement)) {
      return null;
    }
    PsiAssertStatement assertStatement = (PsiAssertStatement)expression.getParent();
    PsiType type = expression.getType();
    if (type == null) return null;
    if (expression == assertStatement.getAssertCondition() && !TypeConversionUtil.isBooleanType(type)) {
      // addTypeCast quickfix is not applicable here since no type can be cast to boolean
      return createIncompatibleTypeHighlightInfo(PsiType.BOOLEAN, type, expression.getTextRange());
    }
    else if (expression == assertStatement.getAssertDescription() && TypeConversionUtil.isVoidType(type)) {
      String description = JavaErrorMessages.message("void.type.is.not.allowed");
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, description);
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkSynchronizedExpressionType(PsiExpression expression) {
    if (expression.getParent() instanceof PsiSynchronizedStatement) {
      PsiType type = expression.getType();
      if (type == null) return null;
      PsiSynchronizedStatement synchronizedStatement = (PsiSynchronizedStatement)expression.getParent();
      if (expression == synchronizedStatement.getLockExpression() &&
          (type instanceof PsiPrimitiveType || TypeConversionUtil.isNullType(type))) {
        PsiClassType objectType = PsiType.getJavaLangObject(expression.getManager(), expression.getResolveScope());
        return createIncompatibleTypeHighlightInfo(objectType, type, expression.getTextRange());
      }
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkConditionalExpressionBranchTypesMatch(PsiExpression expression) {
    if (!(expression.getParent() instanceof PsiConditionalExpression)) {
      return null;
    }
    PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)expression.getParent();
    // check else branches only
    if (conditionalExpression.getElseExpression() != expression) return null;
    final PsiExpression thenExpression = conditionalExpression.getThenExpression();
    assert thenExpression != null;
    PsiType thenType = thenExpression.getType();
    PsiType elseType = expression.getType();
    if (thenType == null || elseType == null) return null;
    if (conditionalExpression.getType() == null) {
      // cannot derive type of conditional expression
      // elsetype will never be castable to thentype, so no quick fix here
      return createIncompatibleTypeHighlightInfo(thenType, elseType, expression.getTextRange());
    }
    return null;
  }

  private static HighlightInfo createIncompatibleTypeHighlightInfo(final PsiType lType, final PsiType rType, final TextRange textRange) {
    PsiType lType1 = lType;
    PsiType rType1 = rType;
    PsiTypeParameter[] lTypeParams = PsiTypeParameter.EMPTY_ARRAY;
    PsiSubstitutor lTypeSubstitutor = PsiSubstitutor.EMPTY;
    if (lType1 instanceof PsiClassType) {
      PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)lType1).resolveGenerics();
      lTypeSubstitutor = resolveResult.getSubstitutor();
      PsiClass psiClass = resolveResult.getElement();
      if (psiClass instanceof PsiAnonymousClass) {
        lType1 = ((PsiAnonymousClass)psiClass).getBaseClassType();
        resolveResult = ((PsiClassType)lType1).resolveGenerics();
        lTypeSubstitutor = resolveResult.getSubstitutor();
        psiClass = resolveResult.getElement();
      }
      lTypeParams = psiClass == null ? PsiTypeParameter.EMPTY_ARRAY : psiClass.getTypeParameters();
    }
    PsiTypeParameter[] rTypeParams = PsiTypeParameter.EMPTY_ARRAY;
    PsiSubstitutor rTypeSubstitutor = PsiSubstitutor.EMPTY;
    if (rType1 instanceof PsiClassType) {
      PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)rType1).resolveGenerics();
      rTypeSubstitutor = resolveResult.getSubstitutor();
      PsiClass psiClass = resolveResult.getElement();
      if (psiClass instanceof PsiAnonymousClass) {
        rType1 = ((PsiAnonymousClass)psiClass).getBaseClassType();
        resolveResult = ((PsiClassType)rType1).resolveGenerics();
        rTypeSubstitutor = resolveResult.getSubstitutor();
        psiClass = resolveResult.getElement();
      }
      rTypeParams = psiClass == null ? PsiTypeParameter.EMPTY_ARRAY : psiClass.getTypeParameters();
    }


    int typeParamColumns = Math.max(lTypeParams.length, rTypeParams.length);
    @NonNls String requredRow = "";
    @NonNls String foundRow = "";
    for (int i = 0; i < typeParamColumns; i++) {
      PsiTypeParameter lTypeParameter = i >= lTypeParams.length ? null : lTypeParams[i];
      PsiTypeParameter rTypeParameter = i >= rTypeParams.length ? null : rTypeParams[i];
      PsiType lSubstedType = lTypeParameter == null ? null : lTypeSubstitutor.substitute(lTypeParameter);
      PsiType rSubstedType = rTypeParameter == null ? null : rTypeSubstitutor.substitute(rTypeParameter);
      boolean matches = Comparing.equal(lSubstedType, rSubstedType);
      @NonNls String openBrace = i == 0 ? "&lt;" : "";
      @NonNls String closeBrace = i == typeParamColumns - 1 ? "&gt;" : ",";
      requredRow += "<td>" + (lTypeParams.length == 0 ? "" : openBrace) + redIfNotMatch(lSubstedType, matches) +
                    (i < lTypeParams.length ? closeBrace : "") + "</td>";
      foundRow += "<td>" + (rTypeParams.length == 0 ? "" : openBrace) + redIfNotMatch(rSubstedType, matches) +
                  (i < rTypeParams.length ? closeBrace : "") + "</td>";
    }
    PsiType lRawType = lType1 instanceof PsiClassType ? ((PsiClassType)lType1).rawType() : lType1;
    PsiType rRawType = rType1 instanceof PsiClassType ? ((PsiClassType)rType1).rawType() : rType1;
    boolean assignable = lRawType == null || rRawType == null || TypeConversionUtil.isAssignable(lRawType, rRawType);

    String toolTip = JavaErrorMessages.message("incompatible.types.html.tooltip", redIfNotMatch(lRawType, assignable), requredRow,
                                               redIfNotMatch(rRawType, assignable), foundRow);

    String description = JavaErrorMessages.message("incompatible.types", formatType(lType1), formatType(rType1));

    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, description, toolTip);
  }

  @Nullable
  public static HighlightInfo checkSingleImportClassConflict(PsiImportStatement statement, Map<String, Pair<PsiImportStatement,PsiClass>> singleImportedClasses) {
    if (statement.isOnDemand()) return null;
    PsiElement element = statement.resolve();
    if (element instanceof PsiClass) {
      String name = ((PsiClass)element).getName();
      Pair<PsiImportStatement, PsiClass> imported = singleImportedClasses.get(name);
      PsiClass importedClass = imported == null ? null : imported.getSecond();
      if (importedClass != null && !element.getManager().areElementsEquivalent(importedClass, element)) {
        String description = JavaErrorMessages.message("single.import.class.conflict", formatClass(importedClass));
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, statement, description);
      }
      singleImportedClasses.put(name, Pair.create(statement, (PsiClass)element));
    }
    return null;
  }


  static HighlightInfo checkConstantExpressionOverflow(PsiExpression expr) {
    boolean overflow = false;
    try {
      if (expr.getUserData(HAS_OVERFLOW_IN_CHILD) == null) {
        expr.getManager().getConstantEvaluationHelper().computeConstantExpression(expr, true);
      }
      else {
        overflow = true;
      }
    }
    catch (ConstantEvaluationOverflowException e) {
      overflow = true;
      return HighlightInfo
        .createHighlightInfo(HighlightInfoType.OVERFLOW_WARNING, expr, JavaErrorMessages.message("numeric.overflow.in.expression"));
    }
    finally {
      PsiElement parent = expr.getParent();
      if (parent instanceof PsiExpression && overflow) {
        parent.putUserData(HAS_OVERFLOW_IN_CHILD, "");
      }
    }

    return null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String redIfNotMatch(PsiType type, boolean matches) {
    if (matches) return getFQName(type, false);
    return "<font color=red><b>" + getFQName(type, true) + "</b></font>";
  }

  private static String getFQName(PsiType type, boolean longName) {
    if (type == null) return "";
    return XmlUtil.escapeString(longName ? type.getInternalCanonicalText() : type.getPresentableText());
  }


  @Nullable
  public static HighlightInfo checkMustBeThrowable(PsiType type, PsiElement context, boolean addCastIntention) {
    if (type == null) return null;
    PsiElementFactory factory = context.getManager().getElementFactory();
    PsiClassType throwable = factory.createTypeByFQClassName("java.lang.Throwable", context.getResolveScope());
    if (!TypeConversionUtil.isAssignable(throwable, type)) {
      HighlightInfo highlightInfo = createIncompatibleTypeHighlightInfo(throwable, type, context.getTextRange());
      if (addCastIntention && TypeConversionUtil.areTypesConvertible(type, throwable)) {
        if (context instanceof PsiExpression) {
          QuickFixAction.registerQuickFixAction(highlightInfo, new AddTypeCastFix(throwable, (PsiExpression)context));
        }
      }
      return highlightInfo;
    }
    return null;
  }


  @Nullable
  private static HighlightInfo checkMustBeThrowable(PsiClass aClass, PsiElement context, boolean addCastIntention) {
    if (aClass == null) return null;
    PsiClassType type = aClass.getManager().getElementFactory().createType(aClass);
    return checkMustBeThrowable(type, context, addCastIntention);
  }


  @Nullable
  public static HighlightInfo checkLabelDefined(PsiIdentifier labelIdentifier, PsiStatement exitedStatement) {
    if (labelIdentifier == null) return null;
    String label = labelIdentifier.getText();
    if (label == null) return null;
    if (exitedStatement == null) {
      String message = JavaErrorMessages.message("unresolved.label", label);
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, labelIdentifier, message);
    }
    return null;
  }

  public static TextRange getMethodDeclarationTextRange(PsiMethod method) {
    int start = method.getModifierList().getTextRange().getStartOffset();
    int end = method.getThrowsList().getTextRange().getEndOffset();
    return new TextRange(start, end);
  }


  @Nullable
  public static HighlightInfo checkReference(PsiJavaCodeReferenceElement ref, JavaResolveResult result, PsiElement resolved) {
    PsiElement refName = ref.getReferenceNameElement();

    if (!(refName instanceof PsiIdentifier) && !(refName instanceof PsiKeyword)) return null;
    HighlightInfo highlightInfo = checkMemberReferencedBeforeConstructorCalled(ref);
    if (highlightInfo != null) return highlightInfo;

    PsiElement refParent = ref.getParent();
    if (!(refParent instanceof PsiMethodCallExpression)) {
      if (resolved == null) {
        // do not highlight unknown packages - javac does not care about illegal package names
        if (isInsidePackageStatement(refName)) return null;
        if (result.isPackagePrefixPackageReference()) return null;
        String description = JavaErrorMessages.message("cannot.resolve.symbol", refName.getText());

        HighlightInfoType type = HighlightInfoType.WRONG_REF;
        if (PsiTreeUtil.getParentOfType(ref, PsiDocComment.class) != null) {
          return null;
        }

        PsiElement parent = PsiTreeUtil.getParentOfType(ref, PsiNewExpression.class, PsiMethod.class);
        HighlightInfo info = HighlightInfo.createHighlightInfo(type, refName, description);
        QuickFixAction.registerQuickFixAction(info, new ImportClassAction(ref));
        QuickFixAction.registerQuickFixAction(info, SetupJDKFix.getInstnace());
        OrderEntryFix.registerFixes(info, ref);
        if (ref instanceof PsiReferenceExpression) {
          TextRange fixRange = HighlightMethodUtil.getFixRange(ref);
          PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
          QuickFixAction.registerQuickFixAction(info, fixRange, new CreateEnumConstantFromUsageAction(refExpr), null, null);
          QuickFixAction.registerQuickFixAction(info, fixRange, new CreateConstantFieldFromUsageAction(refExpr), null, null);
          QuickFixAction.registerQuickFixAction(info, fixRange, new CreateFieldFromUsageAction(refExpr), null, null);
          QuickFixAction.registerQuickFixAction(info, new RenameWrongRefAction(refExpr));
          if (!ref.isQualified()) {
            QuickFixAction.registerQuickFixAction(info, fixRange, new BringVariableIntoScopeAction(refExpr), null, null);
            QuickFixAction.registerQuickFixAction(info, fixRange, new CreateLocalFromUsageAction(refExpr), null, null);
            QuickFixAction.registerQuickFixAction(info, fixRange, new CreateParameterFromUsageAction(refExpr), null, null);
          }
        }
        QuickFixAction.registerQuickFixAction(info, new CreateClassFromUsageAction(ref, CreateClassKind.CLASS));
        QuickFixAction.registerQuickFixAction(info, new CreateClassFromUsageAction(ref, CreateClassKind.INTERFACE));
        QuickFixAction.registerQuickFixAction(info, new CreateClassFromUsageAction(ref, CreateClassKind.ENUM));
        if (parent instanceof PsiNewExpression) {
          QuickFixAction.registerQuickFixAction(info, new CreateClassFromNewAction((PsiNewExpression)parent));
        }
        return info;
      }

      if (!result.isValidResult()) {
        if (!result.isAccessible()) {
          String description = buildProblemWithAccessDescription(ref, result);
          HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF, ref.getReferenceNameElement(), description);
          if (result.isStaticsScopeCorrect()) {
            registerAccessQuickFixAction((PsiMember)resolved, ref, info, result.getCurrentFileResolveScope());
            if (ref instanceof PsiReferenceExpression) {
              QuickFixAction.registerQuickFixAction(info, new RenameWrongRefAction((PsiReferenceExpression)ref));
            }
          }
          return info;
        }

        if (!result.isStaticsScopeCorrect()) {
          String description = buildProblemWithStaticDescription(resolved);
          HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF, ref.getReferenceNameElement(), description);
          if (resolved != null) {
            registerStaticProblemQuickFixAction(resolved, info, ref);
          }
          if (ref instanceof PsiReferenceExpression) {
            QuickFixAction.registerQuickFixAction(info, new RenameWrongRefAction((PsiReferenceExpression)ref));
          }
          return info;
        }
      }
      if ((resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) && !(resolved instanceof ImplicitVariable)) {
        highlightInfo = HighlightControlFlowUtil.checkVariableMustBeFinal((PsiVariable)resolved, ref);
        if (highlightInfo != null) return highlightInfo;
      }
    }
    return highlightInfo;
  }

  private static boolean isInsidePackageStatement(PsiElement element) {
    while (element != null) {
      if (element instanceof PsiPackageStatement) return true;
      if (!(element instanceof PsiIdentifier) && !(element instanceof PsiJavaCodeReferenceElement)) return false;
      element = element.getParent();
    }
    return false;
  }

  @Nullable
  public static HighlightInfo checkElementInReferenceList(PsiJavaCodeReferenceElement ref,
                                                 PsiReferenceList referenceList,
                                                 JavaResolveResult resolveResult) {
    PsiClass resolved = (PsiClass)resolveResult.getElement();
    PsiElement refGrandParent = referenceList.getParent();
    HighlightInfo highlightInfo = null;
    if (refGrandParent instanceof PsiClass) {
      if (refGrandParent instanceof PsiTypeParameter) {
        highlightInfo = GenericsHighlightUtil.checkElementInTypeParameterExtendsList(referenceList, resolveResult, ref);
      }
      else {
        highlightInfo = HighlightClassUtil.checkExtendsClassAndImplementsInterface(referenceList, resolveResult, ref);
        if (highlightInfo == null) {
          highlightInfo = HighlightClassUtil.checkCannotInheritFromFinal(resolved, ref);
        }
        if (highlightInfo == null) {
          highlightInfo = GenericsHighlightUtil.checkCannotInheritFromEnum(resolved, ref);
        }
      }
    }
    else if (refGrandParent instanceof PsiMethod && ((PsiMethod)refGrandParent).getThrowsList() == referenceList) {
      highlightInfo = checkMustBeThrowable(resolved, ref, false);
    }
    return highlightInfo;
  }


  public static boolean shouldHighlight(final PsiElement psiRoot) {
    final HighlightingSettingsPerFile component = HighlightingSettingsPerFile.getInstance(psiRoot.getProject());
    if (component == null) return true;

    final FileHighlighingSetting settingForRoot = component.getHighlightingSettingForRoot(psiRoot);
    return settingForRoot != FileHighlighingSetting.SKIP_HIGHLIGHTING;
  }

  public static void forceRootHighlighting(final PsiElement root, final boolean highlightFlag) {
    final HighlightingSettingsPerFile component = HighlightingSettingsPerFile.getInstance(root.getProject());
    if (component == null) return;
    final PsiFile file = root.getContainingFile();
    final FileHighlighingSetting highlightingLevel =
      highlightFlag ? FileHighlighingSetting.FORCE_HIGHLIGHTING : FileHighlighingSetting.SKIP_HIGHLIGHTING;
    if (file instanceof JspFile && root.getLanguage() instanceof JavaLanguage) {
      //highlight both java roots
      final JspClass jspClass = (JspClass)((JspFile)file).getJavaClass();
      component.setHighlightingSettingForRoot(jspClass.getClassDummyHolder(), highlightingLevel);
      component.setHighlightingSettingForRoot(jspClass.getMethodDummyHolder(), highlightingLevel);
    }
    else {
      component.setHighlightingSettingForRoot(root, highlightingLevel);
    }
  }

  public static boolean shouldInspect(final PsiElement psiRoot) {
    if (!shouldHighlight(psiRoot)) return false;
    final Project project = psiRoot.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFile virtualFile = psiRoot.getContainingFile().getVirtualFile();
    if (virtualFile == null || !virtualFile.isValid()) return false;
    if ((fileIndex.isInLibrarySource(virtualFile) || fileIndex.isInLibraryClasses(virtualFile)) && !fileIndex.isInContent(virtualFile)) {
      return false;
    }
    final HighlightingSettingsPerFile component = HighlightingSettingsPerFile.getInstance(project);
    if (component == null) return true;

    final FileHighlighingSetting settingForRoot = component.getHighlightingSettingForRoot(psiRoot);
    return settingForRoot != FileHighlighingSetting.SKIP_INSPECTION;
  }

  public static void forceRootInspection(final PsiElement root, final boolean inspectionFlag) {
    final HighlightingSettingsPerFile component = HighlightingSettingsPerFile.getInstance(root.getProject());
    if (component == null) return;
    final PsiFile file = root.getContainingFile();
    final FileHighlighingSetting inspectionLevel =
      inspectionFlag ? FileHighlighingSetting.FORCE_HIGHLIGHTING : FileHighlighingSetting.SKIP_INSPECTION;
    if (file instanceof JspFile && root.getLanguage() instanceof JavaLanguage) {
      //highlight both java roots
      final JspClass jspClass = (JspClass)((JspFile)file).getJavaClass();
      component.setHighlightingSettingForRoot(jspClass.getClassDummyHolder(), inspectionLevel);
      component.setHighlightingSettingForRoot(jspClass.getMethodDummyHolder(), inspectionLevel);
    }
    else {
      component.setHighlightingSettingForRoot(root, inspectionLevel);
    }
  }

  public static HighlightInfo convertToHighlightInfo(Annotation annotation) {
    TextAttributes attributes = annotation.getEnforcedTextAttributes();
    if (attributes == null) {
      attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(annotation.getTextAttributes());
    }
    HighlightInfo info = new HighlightInfo(attributes, convertType(annotation), annotation.getStartOffset(),
                                           annotation.getEndOffset(), annotation.getMessage(), annotation.getTooltip(),
                                           annotation.getSeverity(), annotation.isAfterEndOfLine(), annotation.needsUpdateOnTyping());
    info.setGutterIconRenderer(annotation.getGutterIconRenderer());
    info.isFileLevelAnnotation = annotation.isFileLevelAnnotation();
    List<Annotation.QuickFixInfo> fixes = annotation.getQuickFixes();
    if (fixes != null) {
      for (Annotation.QuickFixInfo quickFixInfo : fixes) {
        QuickFixAction
          .registerQuickFixAction(info, quickFixInfo.textRange, quickFixInfo.quickFix, quickFixInfo.options, quickFixInfo.displayName);
      }
    }
    return info;
  }

  public static PsiElement findPsiAtOffset(final PsiFile psiFile, final int textOffset) {
    PsiElement psiElem = psiFile.findElementAt(textOffset);

    while (psiElem != null) {
      if (psiElem instanceof PsiClass || psiElem instanceof PsiMethod || psiElem instanceof PsiField || psiElem instanceof PsiParameter) {
        return psiElem.getTextOffset() == textOffset ? psiElem : null;
      }

      psiElem = psiElem.getParent();
    }
    return null;
  }

  private static HighlightInfoType convertType(Annotation annotation) {
    ProblemHighlightType type = annotation.getHighlightType();
    if (type == ProblemHighlightType.LIKE_UNUSED_SYMBOL) return HighlightInfoType.UNUSED_SYMBOL;
    if (type == ProblemHighlightType.LIKE_UNKNOWN_SYMBOL) return HighlightInfoType.WRONG_REF;
    if (type == ProblemHighlightType.LIKE_DEPRECATED) return HighlightInfoType.DEPRECATED;
    if (type == ProblemHighlightType.J2EE_PROBLEM) {
      return annotation.getSeverity() == HighlightSeverity.ERROR
             ? HighlightInfoType.ERROR
             : annotation.getSeverity() == HighlightSeverity.WARNING ? HighlightInfoType.WARNING
               : annotation.getSeverity() == HighlightSeverity.INFO ? HighlightInfoType.INFO : HighlightInfoType.INFORMATION;
    }
    return annotation.getSeverity() == HighlightSeverity.ERROR
           ? HighlightInfoType.ERROR
           : annotation.getSeverity() == HighlightSeverity.WARNING ? HighlightInfoType.WARNING
             : annotation.getSeverity() == HighlightSeverity.INFO ? HighlightInfoType.INFO : HighlightInfoType.INFORMATION;
  }

  public static boolean isSerializable(PsiClass aClass) {
    PsiManager manager = aClass.getManager();
    PsiClass serializableClass = manager.findClass("java.io.Serializable", aClass.getResolveScope());
    return serializableClass != null && aClass.isInheritor(serializableClass, true);
  }

  public static boolean isSerializationImplicitlyUsedField(PsiField field) {
    final String name = field.getName();
    if (!SERIAL_VERSION_UID_FIELD_NAME.equals(name) && !SERIAL_PERSISTENT_FIELDS_FIELD_NAME.equals(name)) return false;
    if (!field.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = field.getContainingClass();
    return aClass == null || isSerializable(aClass);
  }

  public static HighlightInfo checkClassReferenceAfterQualifier(final PsiReferenceExpression expression, final PsiElement resolved) {
    if (!(resolved instanceof PsiClass)) return null;
    final PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier == null) return null;
    if (qualifier instanceof PsiReferenceExpression) {
      PsiElement qualifierResolved = ((PsiReferenceExpression)qualifier).resolve();
      if (qualifierResolved instanceof PsiClass || qualifierResolved instanceof PsiPackage) return null;
    }
    HighlightInfo info =
      HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, qualifier, JavaErrorMessages.message("expected.class.or.package"));
    QuickFixAction.registerQuickFixAction(info, new RemoveQualifierFix(qualifier, expression, (PsiClass)resolved));
    return info;
  }

  public static List<Problem> convertToProblems(final Collection<HighlightInfo> infos, final VirtualFile file,
                                                 final boolean hasErrorElement) {
    List<Problem> problems = new SmartList<Problem>();
    for (HighlightInfo info : infos) {
      if (info.getSeverity() == HighlightSeverity.ERROR) {
        Problem problem = new ProblemImpl(file, info, hasErrorElement);
        problems.add(problem);
      }
    }
    return problems;
  }

  public static void addErrorsToWolf(final List<HighlightInfo> infos, final PsiFile psiFile, final boolean hasErrorElement) {
    if (!psiFile.getViewProvider().isPhysical()) return; // e.g. errors in evaluate expression
    Project project = psiFile.getProject();
    if (!PsiManager.getInstance(project).isInProject(psiFile)) return; // do not report problems in libraries
    VirtualFile file = psiFile.getVirtualFile();

    List<Problem> problems = convertToProblems(infos, file, hasErrorElement);
    WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(project);
    for (Problem problem : problems) {
      wolf.weHaveGotProblem(problem);
    }
  }
}
