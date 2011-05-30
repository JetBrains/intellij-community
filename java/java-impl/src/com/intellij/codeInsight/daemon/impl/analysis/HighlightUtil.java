/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.quickfix.ChangeVariableTypeQuickFixProvider;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInspection.IntentionProvider;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.scope.processor.VariablesNotProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author cdr
 * Date: Jul 30, 2002
 */
public class HighlightUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil");
  private static final Map<String, Set<String>> ourInterfaceIncompatibleModifiers;
  private static final Map<String, Set<String>> ourMethodIncompatibleModifiers;
  private static final Map<String, Set<String>> ourFieldIncompatibleModifiers;
  private static final Map<String, Set<String>> ourClassIncompatibleModifiers;
  private static final Map<String, Set<String>> ourClassInitializerIncompatibleModifiers;
  private static final Set<String> ourConstructorNotAllowedModifiers;

  @NonNls private static final String SERIAL_VERSION_UID_FIELD_NAME = "serialVersionUID";
  @NonNls private static final String SERIAL_PERSISTENT_FIELDS_FIELD_NAME = "serialPersistentFields";
  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  private HighlightUtil() { }

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
    for (@Modifier String incompatible : incompatibles) {
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

    PsiClass packageLocalClassInTheMiddle = getPackageLocalClassInTheMiddle(place);
    if (packageLocalClassInTheMiddle != null) {
      IntentionAction fix =
        QUICK_FIX_FACTORY.createModifierListFix(packageLocalClassInTheMiddle, PsiModifier.PUBLIC, true, true);
      QuickFixAction.registerQuickFixAction(errorResult, fix);
      return;
    }

    try {
      Project project = refElement.getProject();
      JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      PsiModifierList modifierListCopy = facade.getElementFactory().createFieldFromText("int a;", null).getModifierList();
      modifierListCopy.setModifierProperty(PsiModifier.STATIC, modifierList.hasModifierProperty(PsiModifier.STATIC));
      @Modifier String minModifier = PsiModifier.PACKAGE_LOCAL;
      if (refElement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        minModifier = PsiModifier.PROTECTED;
      }
      if (refElement.hasModifierProperty(PsiModifier.PROTECTED)) {
        minModifier = PsiModifier.PUBLIC;
      }
      String[] modifiers = {PsiModifier.PACKAGE_LOCAL, PsiModifier.PROTECTED, PsiModifier.PUBLIC,};
      PsiClass accessObjectClass = null;
      PsiElement qualifier = place.getQualifier();
      if (qualifier instanceof PsiExpression) {
        accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass((PsiExpression)qualifier).getElement();
      }
      for (int i = ArrayUtil.indexOf(modifiers, minModifier); i < modifiers.length; i++) {
        @Modifier String modifier = modifiers[i];
        modifierListCopy.setModifierProperty(modifier, true);
        if (facade.getResolveHelper().isAccessible(refElement, modifierListCopy, place, accessObjectClass, fileResolveScope)) {
          IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(refElement, modifier, true, true);
          TextRange fixRange = new TextRange(errorResult.startOffset, errorResult.endOffset);
          PsiElement ref = place.getReferenceNameElement();
          if (ref != null) {
            fixRange = fixRange.union(ref.getTextRange());
          }
          QuickFixAction.registerQuickFixAction(errorResult, fixRange, fix, null);
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
              !JavaPsiFacade.getInstance(aClass.getProject()).arePackagesTheSame(aClass, place)) {

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
    PsiTypeElement typeElement = expression.getCheckType();
    if (typeElement == null) return null;
    PsiType checkType = typeElement.getType();
    PsiType operandType = operand.getType();
    if (operandType == null) return null;
    if (TypeConversionUtil.isPrimitiveAndNotNull(operandType)
        || TypeConversionUtil.isPrimitiveAndNotNull(checkType)
        || !TypeConversionUtil.areTypesConvertible(operandType, checkType)) {
      String message = JavaErrorMessages.message("inconvertible.type.cast", formatType(operandType), formatType(checkType));
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, message);
    }
    return null;
  }


  @Nullable
  static HighlightInfo checkInconvertibleTypeCast(PsiTypeCastExpression expression) {
    PsiTypeElement castTypeElement = expression.getCastType();
    if (castTypeElement == null) return null;
    PsiType castType = castTypeElement.getType();

    PsiExpression operand = expression.getOperand();
    if (operand == null) return null;
    PsiType operandType = operand.getType();

    if (operandType != null &&
        !TypeConversionUtil.areTypesConvertible(operandType, castType) &&
        !RedundantCastUtil.isInPolymorphicCall(expression)) {
      String message = JavaErrorMessages.message("inconvertible.type.cast", formatType(operandType), formatType(castType));
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, message);
    }

    return null;
  }

  @Nullable
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
    IElementType opSign = TypeConversionUtil.convertEQtoOperation(eqOpSign);
    if (opSign == null) return null;
    final PsiType lType = assignment.getLExpression().getType();
    final PsiExpression rExpression = assignment.getRExpression();
    if (rExpression == null) return null;
    final PsiType rType = rExpression.getType();
    HighlightInfo errorResult = null;
    if (!TypeConversionUtil.isBinaryOperatorApplicable(opSign, lType, rType, true) ||
        PsiType.getJavaLangObject(assignment.getManager(), assignment.getResolveScope()).equals(lType)) {
      String operatorText = operationSign.getText().substring(0, operationSign.getText().length() - 1);
      String message = JavaErrorMessages.message("binary.operator.not.applicable", operatorText,
                                                 formatType(lType),
                                                 formatType(rType));

      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, assignment, message);
      QuickFixAction.registerQuickFixAction(errorResult, new ChangeToAppendFix(eqOpSign, lType, rType, assignment));
    }
    return errorResult;
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
        registerChangeVariableTypeFixes(leftVar, rType, highlightInfo);
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
    // array initializer checked in checkArrayInitializerApplicable
    if (initializer == null || initializer instanceof PsiArrayInitializerExpression) return null;
    PsiType lType = variable.getType();
    PsiType rType = initializer.getType();
    int start = variable.getTypeElement().getTextRange().getStartOffset();
    int end = variable.getTextRange().getEndOffset();
    HighlightInfo highlightInfo = checkAssignability(lType, rType, initializer, new TextRange(start, end));
    if (highlightInfo != null) {
      registerChangeVariableTypeFixes(variable, rType, highlightInfo);
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
    if (lType == rType) return null;
    if (expression == null) {
      if (rType == null || lType == null || TypeConversionUtil.isAssignable(lType, rType)) return null;
    }
    else if (TypeConversionUtil.areTypesAssignmentCompatible(lType, expression)) {
      return null;
    }
    if (rType == null) {
      rType = expression.getType();
    }
    HighlightInfo highlightInfo = createIncompatibleTypeHighlightInfo(lType, rType, textRange);
    if (rType != null && expression != null && isCastIntentionApplicable(expression, lType)) {
      QuickFixAction.registerQuickFixAction(highlightInfo, new AddTypeCastFix(lType, expression));
    }
    if (expression != null && lType != null) {
      QuickFixAction.registerQuickFixAction(highlightInfo, new WrapExpressionFix(lType, expression));
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
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, statement, description);
    }
    else {
      PsiType returnType = method != null ? method.getReturnType() : null/*JSP page returns void*/;
      boolean isMethodVoid = returnType == null || PsiType.VOID.equals(returnType);
      final PsiExpression returnValue = statement.getReturnValue();
      if (returnValue != null) {
        PsiType valueType = returnValue.getType();
        if (isMethodVoid) {
          description = JavaErrorMessages.message("return.from.void.method");
          errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, statement, description);
          if (valueType != null) {
            IntentionAction fix = QUICK_FIX_FACTORY.createMethodReturnFix(method, valueType, true);
            QuickFixAction.registerQuickFixAction(errorResult, fix);
          }
        }   
        else {
          errorResult = checkAssignability(returnType, valueType, returnValue, statement);
          if (errorResult != null && valueType != null) {
            IntentionAction fix = QUICK_FIX_FACTORY.createMethodReturnFix(method, valueType, true);
            QuickFixAction.registerQuickFixAction(errorResult, fix);
            if (returnType instanceof PsiArrayType && TypeConversionUtil.isAssignable(((PsiArrayType)returnType).getComponentType(), valueType)) {
              QuickFixAction.registerQuickFixAction(errorResult, new SurroundWithArrayFix(null){
                @Override
                protected PsiExpression getExpression(final PsiElement element) {
                  return returnValue.isValid() ? returnValue : null;
                }
              });
            }
          }
        }
        navigationShift = returnValue.getStartOffsetInParent();
      }
      else {
        if (!isMethodVoid) {
          description = JavaErrorMessages.message("missing.return.value");
          errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, statement, description);
          IntentionAction fix = QUICK_FIX_FACTORY.createMethodReturnFix(method, PsiType.VOID, true);
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

  public static String getUnhandledExceptionsDescriptor(final Collection<PsiClassType> unhandled) {
    return getUnhandledExceptionsDescriptor(unhandled, null);
  }

  private static String getUnhandledExceptionsDescriptor(final Collection<PsiClassType> unhandled, final String source) {
    final String exceptions = formatTypes(unhandled);
    return source != null ? JavaErrorMessages.message("unhandled.close.exceptions", exceptions, unhandled.size(), source)
                          : JavaErrorMessages.message("unhandled.exceptions", exceptions, unhandled.size());
  }

  private static String formatTypes(Collection<PsiClassType> unhandled) {
    return StringUtil.join(unhandled, new Function<PsiClassType, String>() {
      @Override public String fun(PsiClassType type) { return formatType(type); }
    }, ", ");
  }

  @Nullable
  static HighlightInfo checkVariableAlreadyDefined(PsiVariable variable) {
    if (variable instanceof ExternallyDefinedPsiElement) return null;
    PsiIdentifier identifier = variable.getNameIdentifier();
    assert identifier != null : variable;
    String name = variable.getName();
    boolean isIncorrect = false;
    if (variable instanceof PsiLocalVariable ||
        variable instanceof PsiParameter && ((PsiParameter)variable).getDeclarationScope() instanceof PsiCatchSection ||
        variable instanceof PsiParameter && ((PsiParameter)variable).getDeclarationScope() instanceof PsiForeachStatement) {
      PsiElement scope = PsiTreeUtil.getParentOfType(variable, PsiFile.class, PsiMethod.class, PsiClassInitializer.class, PsiResourceList.class);
      VariablesNotProcessor proc = new VariablesNotProcessor(variable, false) {
        protected boolean check(final PsiVariable var, final ResolveState state) {
          return (var instanceof PsiLocalVariable || var instanceof PsiParameter) && super.check(var, state);
        }
      };
      PsiScopesUtil.treeWalkUp(proc, identifier, scope);
      if (scope instanceof PsiResourceList && proc.size() == 0) {
        scope = PsiTreeUtil.getParentOfType(variable, PsiFile.class, PsiMethod.class, PsiClassInitializer.class);
        PsiScopesUtil.treeWalkUp(proc, identifier, scope);
      }
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
      if (!(variable instanceof PsiResourceVariable)) {
        QuickFixAction.registerQuickFixAction(highlightInfo, new ReuseVariableDeclarationFix(variable, identifier));
      }
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
    return PsiFormatUtil.formatClass(aClass, PsiFormatUtilBase.SHOW_NAME |
                                             PsiFormatUtilBase.SHOW_ANONYMOUS_CLASS_VERBOSE | (fqn ? PsiFormatUtilBase.SHOW_FQ_NAME : 0));
  }

  @NotNull
  public static String formatMethod(@NotNull PsiMethod method) {
    return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                      PsiFormatUtilBase.SHOW_TYPE);
  }

  @NotNull
  public static String formatType(@Nullable PsiType type) {
    if (type == null) return PsiKeyword.NULL;
    String text = type.getInternalCanonicalText();
    return text == null ? PsiKeyword.NULL : text;
  }


  @Nullable
  public static HighlightInfo checkUnhandledExceptions(final PsiElement element, TextRange fixRange) {
    final List<PsiClassType> unhandledExceptions = ExceptionUtil.getUnhandledExceptions(element);
    if (unhandledExceptions.isEmpty()) return null;

    final HighlightInfoType highlightType = getUnhandledExceptionHighlightType(element);
    if (highlightType == null) return null;

    if (fixRange == null) fixRange = element.getTextRange();
    final String description = getUnhandledExceptionsDescriptor(unhandledExceptions);
    final HighlightInfo errorResult = HighlightInfo.createHighlightInfo(highlightType, fixRange, description);
    registerUnhandledExceptionFixes(element, errorResult, unhandledExceptions);
    return errorResult;
  }

  @Nullable
  public static HighlightInfo checkUnhandledCloserExceptions(final PsiResourceVariable resource) {
    final List<PsiClassType> unhandled = ExceptionUtil.getUnhandledCloserExceptions(resource, null);
    if (unhandled.isEmpty()) return null;

    final HighlightInfoType highlightType = getUnhandledExceptionHighlightType(resource);
    if (highlightType == null) return null;

    final String description = getUnhandledExceptionsDescriptor(unhandled, "auto-closeable resource");
    final HighlightInfo highlight = HighlightInfo.createHighlightInfo(highlightType, resource, description);
    registerUnhandledExceptionFixes(resource, highlight, unhandled);
    return highlight;
  }

  private static void registerUnhandledExceptionFixes(final PsiElement element,
                                                      final HighlightInfo errorResult,
                                                      final List<PsiClassType> unhandled) {
    QuickFixAction.registerQuickFixAction(errorResult, new AddExceptionToCatchFix());
    QuickFixAction.registerQuickFixAction(errorResult, new AddExceptionToThrowsFix(element));
    QuickFixAction.registerQuickFixAction(errorResult, new SurroundWithTryCatchFix(element));
    if (unhandled.size() == 1) {
      QuickFixAction.registerQuickFixAction(errorResult, new GeneralizeCatchFix(element, unhandled.get(0)));
    }
  }

  @Nullable
  private static HighlightInfoType getUnhandledExceptionHighlightType(final PsiElement element) {
    if (!JspPsiUtil.isInJspFile(element)) {
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
      if (new PsiMatcherImpl(statement).ancestor(EnclosingLoopOrSwitchMatcherExpression.INSTANCE).getElement() == null) {
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
      if (new PsiMatcherImpl(statement).ancestor(EnclosingLoopMatcherExpression.INSTANCE).getElement() == null) {
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


  @Nullable
  static HighlightInfo checkIllegalModifierCombination(PsiKeyword keyword, PsiModifierList modifierList) {
    @Modifier String modifier = keyword.getText();
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
    if (parent == null || PsiUtilBase.hasErrorElementChild(parent)) return null;
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
    if (parent == null || PsiUtilBase.hasErrorElementChild(parent)) return null;
    final Map<String, Set<String>> incompatibleModifierMap = getIncompatibleModifierMap(modifierList);
    if (incompatibleModifierMap == null) return null;
    return getIncompatibleModifier(modifier, modifierList, incompatibleModifierMap);
  }


  @Nullable
  public static HighlightInfo checkNotAllowedModifier(PsiKeyword keyword, PsiModifierList modifierList) {
    PsiElement modifierOwner = modifierList.getParent();
    if (modifierOwner == null) return null;
    if (PsiUtilBase.hasErrorElementChild(modifierOwner)) return null;
    @Modifier String modifier = keyword.getText();
    final Map<String, Set<String>> incompatibleModifierMap = getIncompatibleModifierMap(modifierList);
    if (incompatibleModifierMap == null) return null;
    Set<String> incompatibles = incompatibleModifierMap.get(modifier);
    PsiElement modifierOwnerParent = modifierOwner instanceof PsiMember ? ((PsiMember)modifierOwner).getContainingClass() : modifierOwner.getParent();
    if (modifierOwnerParent == null) modifierOwnerParent = modifierOwner.getParent();
    boolean isAllowed = true;
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

        if (aClass.getContainingClass() instanceof PsiAnonymousClass) {
          isAllowed &= !(PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PROTECTED.equals(modifier));
        }
      }
    }
    else if (modifierOwner instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)modifierOwner;
      isAllowed = !(method.isConstructor() && ourConstructorNotAllowedModifiers.contains(modifier));
      PsiClass containingClass = method.getContainingClass();
      if ((method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.PROTECTED)) && method.isConstructor() &&
          containingClass != null && containingClass.isEnum()) {
        isAllowed = false;
      }

      if (PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PROTECTED.equals(modifier) || PsiModifier.TRANSIENT.equals(modifier) ||
          PsiModifier.STRICTFP.equals(modifier) || PsiModifier.SYNCHRONIZED.equals(modifier)) {
        isAllowed &= modifierOwnerParent instanceof PsiClass && !((PsiClass)modifierOwnerParent).isInterface();
      }
    }
    else if (modifierOwner instanceof PsiField) {
      if (PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PROTECTED.equals(modifier) || PsiModifier.TRANSIENT.equals(modifier) ||
          PsiModifier.STRICTFP.equals(modifier) || PsiModifier.SYNCHRONIZED.equals(modifier)) {
        isAllowed = modifierOwnerParent instanceof PsiClass && !((PsiClass)modifierOwnerParent).isInterface();
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
  static HighlightInfo checkLiteralExpressionParsingError(final PsiLiteralExpression expression) {
    final String error = expression.getParsingError();
    if (error != null) {
      final HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, error);
      if (expression instanceof IntentionProvider) {
        QuickFixAction.registerQuickFixActions(info, ((IntentionProvider)expression).getIntentions());
      }
      return info;
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
        final HighlightInfo info = createIncompatibleTypeHighlightInfo(PsiType.BOOLEAN, type, expr.getTextRange());
        if (expr instanceof PsiMethodCallExpression) {
          final PsiMethodCallExpression methodCall = (PsiMethodCallExpression) expr;
          final PsiMethod method = methodCall.resolveMethod();
          if (method != null && PsiType.VOID.equals(method.getReturnType())) {
            IntentionAction fix = QUICK_FIX_FACTORY.createMethodReturnFix(method, PsiType.BOOLEAN, true);
            QuickFixAction.registerQuickFixAction(info, fix);
          }
        }
        return info;
      }
    }
    return null;
  }


  @NotNull
  static Set<PsiClassType> collectUnhandledExceptions(final PsiTryStatement statement) {
    final Set<PsiClassType> thrownTypes = Sets.newHashSet();

    final PsiCodeBlock tryBlock = statement.getTryBlock();
    if (tryBlock != null) {
      thrownTypes.addAll(ExceptionUtil.collectUnhandledExceptions(tryBlock, tryBlock));
    }

    final PsiResourceList resources = statement.getResourceList();
    if (resources != null) {
      thrownTypes.addAll(ExceptionUtil.collectUnhandledExceptions(resources, resources));
    }

    return thrownTypes;
  }

  @Nullable
  static Collection<HighlightInfo> checkExceptionThrownInTry(final PsiParameter parameter, final Set<PsiClassType> thrownTypes) {
    final PsiElement declarationScope = parameter.getDeclarationScope();
    if (!(declarationScope instanceof PsiCatchSection)) return null;

    final PsiType caughtType = parameter.getType();
    if (caughtType instanceof PsiClassType) {
      return checkSimpleCatchParameter(parameter, thrownTypes, (PsiClassType)caughtType);
    }
    else if (caughtType instanceof PsiDisjunctionType) {
      return checkMultiCatchParameter(parameter, thrownTypes);
    }

    return null;
  }

  @Nullable
  private static Collection<HighlightInfo> checkSimpleCatchParameter(final PsiParameter parameter,
                                                                     final Collection<PsiClassType> thrownTypes,
                                                                     final PsiClassType caughtType) {
    if (ExceptionUtil.isUncheckedExceptionOrSuperclass(caughtType)) return null;

    for (PsiClassType exceptionType : thrownTypes) {
      if (exceptionType.isAssignableFrom(caughtType) || caughtType.isAssignableFrom(exceptionType)) return null;
    }

    final String description = JavaErrorMessages.message("exception.never.thrown.try", formatType(caughtType));
    final HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, parameter, description);
    QuickFixAction.registerQuickFixAction(errorResult, new DeleteCatchFix(parameter));
    return Collections.singleton(errorResult);
  }

  @Nullable
  private static Collection<HighlightInfo> checkMultiCatchParameter(final PsiParameter parameter,
                                                                    final Collection<PsiClassType> thrownTypes) {
    final List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
    final Collection<HighlightInfo> highlights = Lists.newArrayListWithCapacity(typeElements.size());

    for (int i = 0, size = typeElements.size(); i < size; i++) {
      final PsiTypeElement typeElement = typeElements.get(i);
      final PsiType catchType = typeElement.getType();
      if (catchType instanceof PsiClassType && ExceptionUtil.isUncheckedExceptionOrSuperclass((PsiClassType)catchType)) continue;

      boolean used = false;
      for (PsiClassType exceptionType : thrownTypes) {
        if (exceptionType.isAssignableFrom(catchType) || catchType.isAssignableFrom(exceptionType)) {
          used = true;
          break;
        }
      }
      if (!used) {
        final String description = JavaErrorMessages.message("exception.never.thrown.try", formatType(catchType));
        final HighlightInfo highlight = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeElement, description);
        QuickFixAction.registerQuickFixAction(highlight, new DeleteMultiCatchFix(typeElement));
        highlights.add(highlight);
      }
    }

    return highlights;
  }


  @Nullable
  static Collection<HighlightInfo> checkWithImprovedCatchAnalysis(final PsiParameter parameter,
                                                                  final Collection<PsiClassType> thrownInTryStatement) {
    final PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiCatchSection)) return null;

    final PsiCatchSection catchSection = (PsiCatchSection)scope;
    final PsiCatchSection[] allCatchSections = catchSection.getTryStatement().getCatchSections();
    final int idx = ArrayUtil.find(allCatchSections, catchSection);
    if (idx <= 0) return null;

    final Collection<PsiClassType> thrownTypes = Sets.newHashSet(thrownInTryStatement);
    thrownTypes.add(PsiType.getJavaLangError(parameter.getManager(), parameter.getResolveScope()));
    thrownTypes.add(PsiType.getJavaLangRuntimeException(parameter.getManager(), parameter.getResolveScope()));
    final Collection<HighlightInfo> result = Lists.newArrayList();

    final List<PsiTypeElement> parameterTypeElements = PsiUtil.getParameterTypeElements(parameter);
    final boolean isMultiCatch = parameterTypeElements.size() > 1;
    for (PsiTypeElement catchTypeElement : parameterTypeElements) {
      final PsiType catchType = catchTypeElement.getType();
      if (ExceptionUtil.isGeneralExceptionType(catchType)) continue;

      // collect exceptions which are caught by this type
      Collection<PsiClassType> caught = ContainerUtil.findAll(thrownTypes, new Condition<PsiClassType>() {
        @Override public boolean value(PsiClassType type) { return catchType.isAssignableFrom(type); }
      });
      if (caught.isEmpty()) continue;
      final Collection<PsiClassType> caughtCopy = Sets.newHashSet(caught);

      // exclude all which are caught by previous catch sections
      for (int i = 0; i < idx; i++) {
        final PsiParameter prevCatchParameter = allCatchSections[i].getParameter();
        if (prevCatchParameter == null) continue;
        for (PsiTypeElement prevCatchTypeElement : PsiUtil.getParameterTypeElements(prevCatchParameter)) {
          final PsiType prevCatchType = prevCatchTypeElement.getType();
          for (Iterator<PsiClassType> iterator = caught.iterator(); iterator.hasNext(); ) {
            if (prevCatchType.isAssignableFrom(iterator.next())) iterator.remove();
          }
          if (caught.isEmpty()) break;
        }
      }

      // check & warn
      if (caught.isEmpty()) {
        final String message = JavaErrorMessages.message("exception.already.caught.warn", formatTypes(caughtCopy), caughtCopy.size());
        final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.WARNING, catchSection, message);
        if (isMultiCatch) {
          QuickFixAction.registerQuickFixAction(highlightInfo, new DeleteMultiCatchFix(catchTypeElement));
        }
        else {
          QuickFixAction.registerQuickFixAction(highlightInfo, new DeleteCatchFix(parameter));
        }
        result.add(highlightInfo);
      }
    }

    return result;
  }


  @Nullable
  static HighlightInfo checkNotAStatement(PsiStatement statement) {
    if (!PsiUtil.isStatement(statement) && !PsiUtilBase.hasErrorElementChild(statement)) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, statement, JavaErrorMessages.message("not.a.statement"));
    }
    return null;
  }


  @Nullable
  public static HighlightInfo checkSwitchSelectorType(PsiSwitchStatement statement) {
    PsiExpression expression = statement.getExpression();
    HighlightInfo errorResult = null;
    if (expression != null && expression.getType() != null) {
      PsiType type = expression.getType();
      if (!isValidTypeForSwitchSelector(type, PsiUtil.isLanguageLevel7OrHigher(expression))) {
        String message =
          JavaErrorMessages.message("incompatible.types", JavaErrorMessages.message("valid.switch.selector.types"), formatType(type));
        errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, message);
        if (PsiType.LONG.equals(type) || PsiType.FLOAT.equals(type) || PsiType.DOUBLE.equals(type)) {
          QuickFixAction.registerQuickFixAction(errorResult, new AddTypeCastFix(PsiType.INT, expression));
        }
      }
    }
    return errorResult;
  }

  public static boolean isValidTypeForSwitchSelector(PsiType type, final boolean languageLevel7OrHigher) {
    if (TypeConversionUtil.getTypeRank(type) <= TypeConversionUtil.INT_RANK) return true;
    if (type instanceof PsiClassType) {
      PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass == null) return false;
      if (psiClass.isEnum()) {
        return true;
      }
      if (languageLevel7OrHigher) {
        return Comparing.strEqual(psiClass.getQualifiedName(), CommonClassNames.JAVA_LANG_STRING);
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
  public static HighlightInfo checkThisOrSuperExpressionInIllegalContext(PsiExpression expr, @Nullable PsiJavaCodeReferenceElement qualifier) {
    if (expr instanceof PsiSuperExpression && !(expr.getParent() instanceof PsiReferenceExpression)) {
      // like in 'Object o = super;'
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expr.getTextRange().getEndOffset(),
                                               expr.getTextRange().getEndOffset() + 1,
                                               JavaErrorMessages.message("dot.expected.after.super.or.this"));
    }
    PsiElement resolved = null;
    PsiClass aClass = qualifier == null ? PsiTreeUtil.getParentOfType(expr, PsiClass.class) : (resolved = qualifier.resolve()) instanceof PsiClass ? (PsiClass)resolved : null;
    if (resolved != null && !(resolved instanceof PsiClass)) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, qualifier, JavaErrorMessages.message("class.expected"));
    }
    if (aClass == null) return null;
    if (qualifier != null && aClass.isInterface()) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, qualifier, HighlightClassUtil.NO_INTERFACE_EXPECTED);
    }
    if (!HighlightClassUtil.hasEnclosingInstanceInScope(aClass, expr, false)) {
      return HighlightClassUtil.reportIllegalEnclosingUsage(expr, null, aClass, expr);
    }

    return null;

  }

  static String buildProblemWithStaticDescription(PsiElement refElement) {
    String type = LanguageFindUsages.INSTANCE.forLanguage(StdLanguages.JAVA).getType(refElement);
    String name = HighlightMessageUtil.getSymbolName(refElement, PsiSubstitutor.EMPTY);
    return JavaErrorMessages.message("non.static.symbol.referenced.from.static.context", type, name);
  }

  static void registerStaticProblemQuickFixAction(@NotNull PsiElement refElement, HighlightInfo errorResult, PsiJavaCodeReferenceElement place) {
    if (refElement instanceof PsiModifierListOwner) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createModifierListFix((PsiModifierListOwner)refElement, PsiModifier.STATIC, true, false));
    }
    // make context non static
    PsiModifierListOwner staticParent = PsiUtil.getEnclosingStaticElement(place, null);
    if (staticParent != null && isInstanceReference(place)) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createModifierListFix(staticParent, PsiModifier.STATIC, false, false));
    }
  }

  private static boolean isInstanceReference(PsiJavaCodeReferenceElement place) {
    PsiElement qualifier = place.getQualifier();
    if (qualifier == null) return true;
    if (!(qualifier instanceof PsiJavaCodeReferenceElement)) return false;
    PsiElement q = ((PsiReference)qualifier).resolve();
    if (q instanceof PsiClass) return false;
    if (q != null) return true;
    String qname = ((PsiJavaCodeReferenceElement)qualifier).getQualifiedName();
    return qname == null || !Character.isLowerCase(qname.charAt(0));
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
  public static HighlightInfo checkCatchParameterIsThrowable(final PsiParameter parameter) {
    if (parameter.getDeclarationScope() instanceof PsiCatchSection) {
      final PsiType type = parameter.getType();
      return checkMustBeThrowable(type, parameter, true);
    }
    return null;
  }

  @Nullable
  public static HighlightInfo checkTryResourceIsAutoCloseable(@NotNull final PsiResourceVariable resource) {
    final PsiType type = resource.getType();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(resource.getProject()).getElementFactory();
    final PsiClassType autoCloseable = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, resource.getResolveScope());
    if (TypeConversionUtil.isAssignable(autoCloseable, type)) return null;

    return createIncompatibleTypeHighlightInfo(autoCloseable, type, resource.getTextRange());
  }

  @Nullable
  public static Collection<HighlightInfo> checkArrayInitializer(final PsiExpression initializer) {
    if (! (initializer instanceof PsiArrayInitializerExpression)) return null;

    final PsiType arrayInitializerType = initializer.getType();
    if (! (arrayInitializerType instanceof PsiArrayType)) return null;

    final PsiType componentType = ((PsiArrayType) arrayInitializerType).getComponentType();
    final PsiArrayInitializerExpression arrayInitializer = (PsiArrayInitializerExpression) initializer;

    boolean arrayTypeFixChecked = false;
    VariableArrayTypeFix fix = null;

    final Collection<HighlightInfo> result = Lists.newArrayList();
    final PsiExpression[] initializers = arrayInitializer.getInitializers();
    for (PsiExpression expression : initializers) {
      final HighlightInfo info = checkArrayInitializerCompatibleTypes(expression, componentType);
      if (info != null) {
        result.add(info);

        if (!arrayTypeFixChecked) {
          final PsiType checkResult = sameType(initializers);
          fix = checkResult != null ? new VariableArrayTypeFix(arrayInitializer, checkResult) : null;
          arrayTypeFixChecked = true;
        }
        if (fix != null) {
          QuickFixAction.registerQuickFixAction(info, fix);
        }
      }
    }
    return result;
  }

  @Nullable
  private static PsiType getArrayInitializerType(final PsiArrayInitializerExpression element) {
    final PsiType typeCheckResult = sameType(element.getInitializers());
    if (typeCheckResult != null) {
      return typeCheckResult.createArrayType();
    }
    return null;
  }

  @Nullable
  public static PsiType sameType(PsiExpression[] expressions) {
    PsiType type = null;
    for (PsiExpression expression : expressions) {
      final PsiType currentType;
      if (expression instanceof PsiArrayInitializerExpression) {
        currentType = getArrayInitializerType((PsiArrayInitializerExpression)expression);
      }
      else {
        currentType = expression.getType();
      }
      if (type == null) {
        type = currentType;
      }
      else if (!type.equals(currentType)) {
        return null;
      }
    }
    return type;
  }

  @Nullable
  private static HighlightInfo checkArrayInitializerCompatibleTypes(PsiExpression initializer, final PsiType componentType) {
    PsiType initializerType = initializer.getType();
    if (initializerType == null) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, initializer,
                                               JavaErrorMessages.message("illegal.initializer", formatType(componentType)));
    }
    PsiExpression expression = initializer instanceof PsiArrayInitializerExpression ? null : initializer;
    return checkAssignability(componentType, initializerType, expression, initializer);
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
    else if (parent instanceof PsiNewExpression || parent instanceof PsiArrayInitializerExpression) {
      return null;
    }

    HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, JavaErrorMessages.message("array.initializer.not.allowed"));
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

    // Every case constant expression associated with a switch statement must be assignable ($5.2) to the type of the switch Expression.
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
      String description = JavaErrorMessages.message("switch.colon.expected.after.case.label");
      CharSequence chars = statement.getContainingFile().getViewProvider().getContents();
      boolean isAfterEndOfLine = end >= chars.length() || chars.charAt(start) == '\n' || chars.charAt(start) == '\r';
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, null,start, end, description, description,isAfterEndOfLine, null);
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

    if (PsiUtil.isInsideJavadocComment(typeElement)) return null;

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
      else if (// like in Class c = void.class;
               typeOwner instanceof PsiClassObjectAccessExpression &&
               TypeConversionUtil.isVoidType(((PsiClassObjectAccessExpression)typeOwner).getOperand().getType()) ||
               // do not highlight incomplete declarations
               typeOwner != null && PsiUtilBase.hasErrorElementChild(typeOwner)) {
        return null;
      }
      else if (typeOwner instanceof JavaCodeFragment) {
        if (typeOwner.getUserData(PsiUtil.VALID_VOID_TYPE_IN_CODE_FRAGMENT) != null) return null;
      }
    }

    String description = JavaErrorMessages.message("illegal.type.void");
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, type, description);
  }

  @Nullable
  public static HighlightInfo checkMemberReferencedBeforeConstructorCalled(PsiElement expression, PsiElement resolved) {
    PsiClass referencedClass;
    @NonNls String resolvedName;
    PsiType type;
    if (expression instanceof PsiJavaCodeReferenceElement) {
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
        resolvedName = PsiFormatUtil.formatVariable(referencedField, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
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
          resolvedName = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                                                                  PsiFormatUtilBase.SHOW_NAME, 0);
          if (referencedClass == null) referencedClass = method.getContainingClass();
        }
      }
      else if (resolved instanceof PsiClass) {
        PsiClass aClass = (PsiClass)resolved;
        if (aClass.hasModifierProperty(PsiModifier.STATIC)) return null;
        referencedClass = aClass.getContainingClass();
        if (referencedClass == null) return null;
        resolvedName = PsiFormatUtil.formatClass(aClass, PsiFormatUtilBase.SHOW_NAME);
      }
      else {
        return null;
      }
    }
    else if (expression instanceof PsiThisExpression) {
      PsiThisExpression thisExpression = (PsiThisExpression)expression;
      type = thisExpression.getType();
      referencedClass = PsiUtil.resolveClassInType(type);
      if (thisExpression.getQualifier() != null) {
        resolvedName = referencedClass == null
                       ? null
                       : PsiFormatUtil.formatClass(referencedClass, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME) + ".this";
      }
      else {
        resolvedName = "this";
      }
    }
    else {
      return null;
    }
    if (referencedClass == null) return null;
    return checkReferenceToOurInstanceInsideThisOrSuper(expression, referencedClass, resolvedName);
  }

  @Nullable
  private static HighlightInfo checkReferenceToOurInstanceInsideThisOrSuper(final PsiElement expression,
                                                                            final PsiClass referencedClass,
                                                                            final String resolvedName) {
    if (PsiTreeUtil.getParentOfType(expression, PsiReferenceParameterList.class) != null) return null;
    PsiElement element = expression.getParent();
    while (element != null) {
      // check if expression inside super()/this() call
      if (isSuperOrThisMethodCall(element)) {
        PsiElement parentClass = new PsiMatcherImpl(element)
          .parent(PsiMatchers.hasClass(PsiExpressionStatement.class))
          .parent(PsiMatchers.hasClass(PsiCodeBlock.class))
          .parent(PsiMatchers.hasClass(PsiMethod.class))
          .dot(PsiMatchers.isConstructor(true))
          .parent(PsiMatchers.hasClass(PsiClass.class))
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
        final HighlightInfo highlightInfo = createMemberReferencedError(resolvedName, expression.getTextRange());
        if (expression instanceof PsiReferenceExpression && PsiUtil.isInnerClass(aClass)) {
          final String referenceName = ((PsiReferenceExpression)expression).getReferenceName();
          final PsiClass containingClass = aClass.getContainingClass();
          LOG.assertTrue(containingClass != null);
          final PsiField fieldInContainingClass = containingClass.findFieldByName(referenceName, true);
          if (fieldInContainingClass != null && ((PsiReferenceExpression)expression).getQualifierExpression() == null) {
            QuickFixAction.registerQuickFixAction(highlightInfo, new QualifyWithThisFix(containingClass, expression));
          }
        }

        return highlightInfo;
      }
      element = element.getParent();
    }
    return null;
  }

  private static HighlightInfo createMemberReferencedError(@NonNls final String resolvedName, TextRange textRange) {
    String description = JavaErrorMessages.message("member.referenced.before.constructor.called", resolvedName);
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, description);
  }

  @Nullable
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
      TextRange range = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      return createMemberReferencedError(aClass.getName()+".this", range);
    }
    for (PsiMethod constructor : constructors) {
      if (!isSuperCalledInConstructor(constructor)) {
        return createMemberReferencedError(aClass.getName()+".this", HighlightNamesUtil.getMethodDeclarationTextRange(constructor));
      }
    }
    return null;
  }

  private static boolean isSuperCalledInConstructor(final PsiMethod constructor) {
    final PsiCodeBlock body = constructor.getBody();
    if (body == null) return false;
    final PsiStatement[] statements = body.getStatements();
    if (statements.length == 0) return false;
    final PsiStatement statement = statements[0];
    final PsiElement element = new PsiMatcherImpl(statement)
        .dot(PsiMatchers.hasClass(PsiExpressionStatement.class))
        .firstChild(PsiMatchers.hasClass(PsiMethodCallExpression.class))
        .firstChild(PsiMatchers.hasClass(PsiReferenceExpression.class))
        .firstChild(PsiMatchers.hasClass(PsiKeyword.class))
        .dot(PsiMatchers.hasText(PsiKeyword.SUPER))
        .getElement();
    return element != null;
  }

  @Nullable
  private static String getMethodExpressionName(PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression)) return null;
    PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)element).getMethodExpression();
    return methodExpression.getReferenceName();
  }
  public static boolean isSuperOrThisMethodCall(PsiElement element) {
    String name = getMethodExpressionName(element);
    return PsiKeyword.SUPER.equals(name) || PsiKeyword.THIS.equals(name);
  }
  public static boolean isSuperMethodCall(PsiElement element) {
    String name = getMethodExpressionName(element);
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
  static Collection<HighlightInfo> checkCatchTypeIsDisjoint(final PsiParameter parameter) {
    if (!(parameter.getType() instanceof PsiDisjunctionType)) return null;

    final Collection<HighlightInfo> result = Lists.newArrayList();
    final List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
    for (int i = 0, size = typeElements.size(); i < size; i++) {
      final PsiClass class1 = PsiUtil.resolveClassInClassTypeOnly(typeElements.get(i).getType());
      if (class1 == null) continue;
      for (int j = i + 1; j < size; j++) {
        final PsiClass class2 = PsiUtil.resolveClassInClassTypeOnly(typeElements.get(j).getType());
        if (class2 == null) continue;
        final boolean sub = InheritanceUtil.isInheritorOrSelf(class1, class2, true);
        final boolean sup = InheritanceUtil.isInheritorOrSelf(class2, class1, true);
        if (sub || sup) {
          final String name1 = PsiFormatUtil.formatClass(class1, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
          final String name2 = PsiFormatUtil.formatClass(class2, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
          final String message = JavaErrorMessages.message("exception.must.be.disjoint", (sub ? name1 : name2), (sub ? name2 : name1));
          result.add(HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeElements.get(sub ? i : j), message));
          break;
        }
      }
    }

    return result;
  }


  @Nullable
  static Collection<HighlightInfo> checkExceptionAlreadyCaught(final PsiParameter parameter) {
    final PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiCatchSection)) return null;

    final PsiCatchSection catchSection = (PsiCatchSection)scope;
    final PsiCatchSection[] allCatchSections = catchSection.getTryStatement().getCatchSections();
    final int startFrom = ArrayUtil.find(allCatchSections, catchSection) - 1;
    if (startFrom < 0) return null;

    final List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
    final boolean isInMultiCatch = typeElements.size() > 1;
    final Collection<HighlightInfo> result = Lists.newArrayList();

    for (PsiTypeElement typeElement : typeElements) {
      final PsiClass catchClass = PsiUtil.resolveClassInClassTypeOnly(typeElement.getType());
      if (catchClass == null) continue;

      for (int i = startFrom; i >= 0; i--) {
        final PsiCatchSection upperCatchSection = allCatchSections[i];
        final PsiType upperCatchType = upperCatchSection.getCatchType();

        final boolean highlight = upperCatchType instanceof PsiDisjunctionType
                                  ? checkMultipleTypes(catchClass, ((PsiDisjunctionType)upperCatchType).getDisjunctions())
                                  : checkSingleType(catchClass, upperCatchType);
        if (highlight) {
          final String className = PsiFormatUtil.formatClass(catchClass, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
          final String description = JavaErrorMessages.message("exception.already.caught", className);
          final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeElement, description);
          result.add(highlightInfo);

          QuickFixAction.registerQuickFixAction(highlightInfo, new MoveCatchUpFix(catchSection, upperCatchSection));
          if (isInMultiCatch) {
            QuickFixAction.registerQuickFixAction(highlightInfo, new DeleteMultiCatchFix(typeElement));
          }
          else {
            QuickFixAction.registerQuickFixAction(highlightInfo, new DeleteCatchFix(parameter));
          }
        }
      }
    }

    return result.size() > 0 ? result : null;
  }

  private static boolean checkMultipleTypes(final PsiClass catchClass, final List<PsiType> upperCatchTypes) {
    for (int i = upperCatchTypes.size() - 1; i >= 0; i--) {
      if (checkSingleType(catchClass, upperCatchTypes.get(i))) return true;
    }
    return false;
  }

  private static boolean checkSingleType(final PsiClass catchClass, final PsiType upperCatchType) {
    final PsiClass upperCatchClass = PsiUtil.resolveClassInType(upperCatchType);
    return upperCatchClass != null && InheritanceUtil.isInheritorOrSelf(catchClass, upperCatchClass, true);
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
    @Language("HTML") @NonNls String requiredRow = "";
    @Language("HTML") @NonNls String foundRow = "";
    for (int i = 0; i < typeParamColumns; i++) {
      PsiTypeParameter lTypeParameter = i >= lTypeParams.length ? null : lTypeParams[i];
      PsiTypeParameter rTypeParameter = i >= rTypeParams.length ? null : rTypeParams[i];
      PsiType lSubstitutedType = lTypeParameter == null ? null : lTypeSubstitutor.substitute(lTypeParameter);
      PsiType rSubstitutedType = rTypeParameter == null ? null : rTypeSubstitutor.substitute(rTypeParameter);
      boolean matches = Comparing.equal(lSubstitutedType, rSubstitutedType);
      @NonNls String openBrace = i == 0 ? "&lt;" : "";
      @NonNls String closeBrace = i == typeParamColumns - 1 ? "&gt;" : ",";
      requiredRow += "<td>" + (lTypeParams.length == 0 ? "" : openBrace) + redIfNotMatch(lSubstitutedType, matches) +
                     (i < lTypeParams.length ? closeBrace : "") + "</td>";
      foundRow += "<td>" + (rTypeParams.length == 0 ? "" : openBrace) + redIfNotMatch(rSubstitutedType, matches) +
                  (i < rTypeParams.length ? closeBrace : "") + "</td>";
    }
    PsiType lRawType = lType1 instanceof PsiClassType ? ((PsiClassType)lType1).rawType() : lType1;
    PsiType rRawType = rType1 instanceof PsiClassType ? ((PsiClassType)rType1).rawType() : rType1;
    boolean assignable = lRawType == null || rRawType == null || TypeConversionUtil.isAssignable(lRawType, rRawType);

    String toolTip = JavaErrorMessages.message("incompatible.types.html.tooltip",
                                               redIfNotMatch(lRawType, assignable), requiredRow,
                                               redIfNotMatch(rRawType, assignable), foundRow);

    String description = JavaErrorMessages.message("incompatible.types", formatType(lType1), formatType(rType1));

    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, null, textRange.getStartOffset(), textRange.getEndOffset(),
                                             description, toolTip);
  }

  @Nullable
  public static HighlightInfo checkSingleImportClassConflict(PsiImportStatement statement,
                                                             Map<String, Pair<PsiImportStaticReferenceElement, PsiClass>> singleImportedClasses) {
    if (statement.isOnDemand()) return null;
    PsiElement element = statement.resolve();
    if (element instanceof PsiClass) {
      String name = ((PsiClass)element).getName();
      Pair<PsiImportStaticReferenceElement, PsiClass> imported = singleImportedClasses.get(name);
      PsiClass importedClass = imported == null ? null : imported.getSecond();
      if (importedClass != null && !element.getManager().areElementsEquivalent(importedClass, element)) {
        String description = JavaErrorMessages.message("single.import.class.conflict", formatClass(importedClass));
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, statement, description);
      }
      singleImportedClasses.put(name, Pair.create((PsiImportStaticReferenceElement)statement.getReference(), (PsiClass)element));
    }
    return null;
  }


  @NonNls
  private static String redIfNotMatch(PsiType type, boolean matches) {
    if (matches) return getFQName(type, false);
    return "<font color=red><b>" + getFQName(type, true) + "</b></font>";
  }

  private static String getFQName(PsiType type, boolean longName) {
    if (type == null) return "";
    return XmlStringUtil.escapeString(longName ? type.getInternalCanonicalText() : type.getPresentableText());
  }


  @Nullable
  public static HighlightInfo checkMustBeThrowable(PsiType type, PsiElement context, boolean addCastIntention) {
    if (type == null) return null;
    PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
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
  private static HighlightInfo checkMustBeThrowable(PsiClass aClass, PsiElement context) {
    if (aClass == null) return null;
    PsiClassType type = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(aClass);
    return checkMustBeThrowable(type, context, false);
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


  @Nullable
  public static HighlightInfo checkReference(PsiJavaCodeReferenceElement ref, JavaResolveResult result, PsiElement resolved) {
    PsiElement refName = ref.getReferenceNameElement();

    if (!(refName instanceof PsiIdentifier) && !(refName instanceof PsiKeyword)) return null;
    HighlightInfo highlightInfo = checkMemberReferencedBeforeConstructorCalled(ref, resolved);
    if (highlightInfo != null) return highlightInfo;

    PsiElement refParent = ref.getParent();
    if (refParent instanceof PsiMethodCallExpression) {
      return null;  // methods checked elsewhere
    }
    if (resolved == null) {
      // do not highlight unknown packages - javac does not care about illegal package names
      if (isInsidePackageStatement(refName)) return null;
      if (result.isPackagePrefixPackageReference()) return null;
      JavaResolveResult[] results = ref.multiResolve(true);
      String description;
      if (results.length > 1) {
        String t1 = format(results[0].getElement());
        String t2 = format(results[1].getElement());
        description = JavaErrorMessages.message("ambiguous.reference", refName.getText(), t1, t2);
      }
      else {
        description = JavaErrorMessages.message("cannot.resolve.symbol", refName.getText());
      }

      HighlightInfoType type = HighlightInfoType.WRONG_REF;
      if (PsiUtil.isInsideJavadocComment(ref)) return null;

      HighlightInfo info = HighlightInfo.createHighlightInfo(type, refName, description);

      UnresolvedReferenceQuickFixProvider.registerReferenceFixes(ref, new QuickFixActionRegistrarImpl(info));
      return info;
    }

    if (!result.isValidResult() && !PsiUtil.isInsideJavadocComment(ref)) {
      if (!result.isAccessible()) {
        String description = buildProblemWithAccessDescription(ref, result);
        HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF, ref.getReferenceNameElement(), description);
        if (result.isStaticsScopeCorrect()) {
          registerAccessQuickFixAction((PsiMember)resolved, ref, info, result.getCurrentFileResolveScope());
          if (ref instanceof PsiReferenceExpression) {
            QuickFixAction.registerQuickFixAction(info, new RenameWrongRefFix((PsiReferenceExpression)ref));
          }
        }
        return info;
      }

      if (!result.isStaticsScopeCorrect()) {
        String description = buildProblemWithStaticDescription(resolved);
        HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF, ref.getReferenceNameElement(), description);
        registerStaticProblemQuickFixAction(resolved, info, ref);
        if (ref instanceof PsiReferenceExpression) {
          QuickFixAction.registerQuickFixAction(info, new RenameWrongRefFix((PsiReferenceExpression)ref));
        }
        return info;
      }
    }
    if ((resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) && !(resolved instanceof ImplicitVariable)) {
      highlightInfo = HighlightControlFlowUtil.checkVariableMustBeFinal((PsiVariable)resolved, ref);
    }
    return highlightInfo;
  }

  private static String format(PsiElement element) {
    if (element instanceof PsiClass) return formatClass((PsiClass)element);
    if (element instanceof PsiMethod) return formatMethod((PsiMethod)element);
    return ElementDescriptionUtil.getElementDescription(element, HighlightUsagesDescriptionLocation.INSTANCE);
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
    PsiElement resolved = resolveResult.getElement();
    HighlightInfo highlightInfo = null;
    PsiElement refGrandParent = referenceList.getParent();
    if (resolved instanceof PsiClass) {
      PsiClass aClass = (PsiClass)resolved;
      if (refGrandParent instanceof PsiClass) {
        if (refGrandParent instanceof PsiTypeParameter) {
          highlightInfo = GenericsHighlightUtil.checkElementInTypeParameterExtendsList(referenceList, resolveResult, ref);
        }
        else {
          highlightInfo = HighlightClassUtil.checkExtendsClassAndImplementsInterface(referenceList, resolveResult, ref);
          if (highlightInfo == null) {
            highlightInfo = HighlightClassUtil.checkCannotInheritFromFinal(aClass, ref);
          }
          if (highlightInfo == null) {
            highlightInfo = GenericsHighlightUtil.checkCannotInheritFromEnum(aClass, ref);
          }
          if (highlightInfo == null) {
            highlightInfo = GenericsHighlightUtil.checkCannotInheritFromTypeParameter(aClass, ref);
          }
        }
      }
      else if (refGrandParent instanceof PsiMethod && ((PsiMethod)refGrandParent).getThrowsList() == referenceList) {
        highlightInfo = checkMustBeThrowable(aClass, ref);
      }
    }
    else if (refGrandParent instanceof PsiMethod && referenceList == ((PsiMethod)refGrandParent).getThrowsList()) {
      highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, ref, JavaErrorMessages.message("class.name.expected"));
    }
    return highlightInfo;
  }


  public static boolean isSerializable(PsiClass aClass) {
    PsiManager manager = aClass.getManager();
    PsiClass serializableClass = JavaPsiFacade.getInstance(manager.getProject()).findClass("java.io.Serializable", aClass.getResolveScope());
    return serializableClass != null && aClass.isInheritor(serializableClass, true);
  }

  public static boolean isSerializationImplicitlyUsedField(PsiField field) {
    final String name = field.getName();
    if (!SERIAL_VERSION_UID_FIELD_NAME.equals(name) && !SERIAL_PERSISTENT_FIELDS_FIELD_NAME.equals(name)) return false;
    if (!field.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = field.getContainingClass();
    return aClass == null || isSerializable(aClass);
  }

  @Nullable
  public static HighlightInfo checkClassReferenceAfterQualifier(final PsiReferenceExpression expression, final PsiElement resolved) {
    if (!(resolved instanceof PsiClass)) return null;
    final PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier == null) return null;
    if (qualifier instanceof PsiReferenceExpression) {
      PsiElement qualifierResolved = ((PsiReferenceExpression)qualifier).resolve();
      if (qualifierResolved instanceof PsiClass || qualifierResolved instanceof PsiPackage) return null;
    }
    HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, qualifier, JavaErrorMessages.message("expected.class.or.package"));
    QuickFixAction.registerQuickFixAction(info, new RemoveQualifierFix(qualifier, expression, (PsiClass)resolved));
    return info;
  }

  public static void registerChangeVariableTypeFixes(PsiVariable parameter, PsiType itemType, HighlightInfo highlightInfo) {
    for (ChangeVariableTypeQuickFixProvider fixProvider : Extensions.getExtensions(ChangeVariableTypeQuickFixProvider.EP_NAME)) {
      for (IntentionAction action : fixProvider.getFixes(parameter, itemType)) {
        QuickFixAction.registerQuickFixAction(highlightInfo, action, null);
      }
    }
  }

  @Nullable
  static HighlightInfo checkAnnotationMethodParameters(PsiParameterList list) {
    if (list.getParent() instanceof PsiAnnotationMethod && list.getParametersCount() > 0) {
      final String message = JavaErrorMessages.message("annotation.interface.members.may.not.have.parameters");
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, message);
    }
    return null;
  }
}
