/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util;

import com.intellij.aspects.psi.PsiIdPattern;
import com.intellij.aspects.psi.PsiTypeNamePattern;
import com.intellij.aspects.psi.PsiTypeNamePatternElement;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.jsp.JspElement;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

public final class PsiUtil {
  public static final int ACCESS_LEVEL_PUBLIC = 4;
  public static final int ACCESS_LEVEL_PROTECTED = 3;
  public static final int ACCESS_LEVEL_PACKAGE_LOCAL = 2;
  public static final int ACCESS_LEVEL_PRIVATE = 1;
  public static final Key<Boolean> VALID_VOID_TYPE_IN_CODE_FRAGMENT = Key.create("VALID_VOID_TYPE_IN_CODE_FRAGMENT");

  private PsiUtil() {}

  public static boolean isOnAssignmentLeftHand(PsiExpression expr) {
    PsiElement parent = expr.getParent();
    return parent instanceof PsiAssignmentExpression
        && expr.equals(((PsiAssignmentExpression) parent).getLExpression());
  }

  public static boolean isAccessibleFromPackage(PsiModifierListOwner element, PsiPackage aPackage) {
    if (element.hasModifierProperty(PsiModifier.PUBLIC)) return true;
    if (element.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    return element.getManager().isInPackage(element, aPackage);
  }

  /**
   * @deprecated Use {@link PsiManager#isInPackage(com.intellij.psi.PsiElement, com.intellij.psi.PsiPackage)}
   * instead.
   */
  public static boolean isInPackage(PsiElement element, PsiPackage aPackage) {
    return element.getManager().isInPackage(element, aPackage);
  }

  public static boolean isAccessedForWriting(PsiExpression expr) {
    if (isOnAssignmentLeftHand(expr)) return true;
    PsiElement parent = expr.getParent();
    if (parent instanceof PsiPrefixExpression) {
      PsiJavaToken sign = ((PsiPrefixExpression) parent).getOperationSign();
      IElementType tokenType = sign.getTokenType();
      return tokenType == JavaTokenType.PLUSPLUS || tokenType == JavaTokenType.MINUSMINUS;
    }
    else if (parent instanceof PsiPostfixExpression) {
      PsiJavaToken sign = ((PsiPostfixExpression) parent).getOperationSign();
      IElementType tokenType = sign.getTokenType();
      return tokenType == JavaTokenType.PLUSPLUS || tokenType == JavaTokenType.MINUSMINUS;
    }
    else {
      return false;
    }
  }

  public static boolean isAccessedForReading(PsiExpression expr) {
    PsiElement parent = expr.getParent();
    if (parent instanceof PsiAssignmentExpression && expr.equals(((PsiAssignmentExpression) parent).getLExpression())) {
      return ((PsiAssignmentExpression)parent).getOperationSign().getTokenType() != JavaTokenType.EQ;
    }
    else {
      return true;
    }
  }

  /**
   * @deprecated Use {@link PsiResolveHelper#isAccessible(com.intellij.psi.PsiMember, com.intellij.psi.PsiElement, com.intellij.psi.PsiClass)}
   */
  public static boolean isAccessible(PsiMember member, PsiElement place, PsiClass accessObjectClass) {
    return place.getManager().getResolveHelper().isAccessible(member, place, accessObjectClass);
  }

  /**
   * @deprecated Use {@link PsiResolveHelper#isAccessible(com.intellij.psi.PsiMember, com.intellij.psi.PsiModifierList, com.intellij.psi.PsiElement, com.intellij.psi.PsiClass)}
   */
  public static boolean isAccessible(PsiMember member, PsiModifierList modifierList,
                                     PsiElement place, PsiClass accessObjectClass) {
    return place.getManager().getResolveHelper().isAccessible(member, modifierList, place, accessObjectClass);
  }




  public static ResolveResult getAccessObjectClass(PsiExpression accessObject) {
    if (accessObject instanceof PsiSuperExpression) {
      final PsiJavaCodeReferenceElement qualifier = ((PsiSuperExpression) accessObject).getQualifier();
      if (qualifier != null) { // E.super.f
        final ResolveResult result = qualifier.advancedResolve(false);
        final PsiElement resolve = result.getElement();
        if (resolve instanceof PsiClass) {
          final PsiClass psiClass;
          final PsiSubstitutor substitutor;
          if (resolve instanceof PsiTypeParameter) {
            final PsiClassType parameterType = resolve.getManager().getElementFactory().createType((PsiTypeParameter) resolve);
            final PsiType superType = result.getSubstitutor().substitute(parameterType);
            if (superType instanceof PsiArrayType) {
              return resolve.getManager().getElementFactory().getArrayClassType(((PsiArrayType)superType).getComponentType()).resolveGenerics(); 
            }
            else if (superType instanceof PsiClassType) {
              final PsiClassType type = (PsiClassType)superType;
              substitutor = type.resolveGenerics().getSubstitutor();
              psiClass = type.resolve();
            }
            else {
              psiClass = null;
              substitutor = PsiSubstitutor.EMPTY;
            }
          }
          else {
            psiClass = (PsiClass) resolve;
            substitutor = PsiSubstitutor.EMPTY;
          }
          if (psiClass != null) {
            return new CandidateInfo(psiClass, substitutor);
          }
          else
            return ResolveResult.EMPTY;
        }
        return ResolveResult.EMPTY;
      }
      else {
        PsiElement scope = accessObject.getContext();
        PsiElement lastParent = accessObject;
        while (scope != null) {
          if (scope instanceof PsiClass) {
            if (scope instanceof PsiAnonymousClass) {
              if (lastParent instanceof PsiExpressionList) {
                lastParent = scope;
                scope = scope.getContext();
                continue;
              }
            }
            return new CandidateInfo(scope, PsiSubstitutor.EMPTY);
          }
          lastParent = scope;
          scope = scope.getContext();
        }
        return ResolveResult.EMPTY;
      }
    }
    else {
      PsiType type = accessObject.getType();
      if (!(type instanceof PsiClassType || type instanceof PsiArrayType)) return ResolveResult.EMPTY;
      return PsiUtil.resolveGenericsClassInType(type);
    }
  }

  public static PsiModifierList getModifierList(PsiElement element) {
    if (element instanceof PsiModifierListOwner) {
      return ((PsiModifierListOwner) element).getModifierList();
    }
    else {
      return null;
    }
  }

  public static boolean hasModifierProperty(PsiModifierListOwner owner, String property) {
    final PsiModifierList modifierList = owner.getModifierList();
    return modifierList == null ? false : modifierList.hasModifierProperty(property);
  }

  public static boolean isConstantExpression(PsiExpression expression) {
    IsConstantExpressionVisitor visitor = new IsConstantExpressionVisitor();
    expression.accept(visitor);
    return visitor.myIsConstant;
  }

  // todo: move to PsiThrowsList?
  public static void addException(PsiMethod method, String exceptionFQName) throws IncorrectOperationException {
    PsiClass exceptionClass = method.getManager().findClass(exceptionFQName, method.getResolveScope());
    addException(method, exceptionClass, exceptionFQName);
  }

  public static void addException(PsiMethod method, PsiClass exceptionClass) throws IncorrectOperationException {
    addException(method, exceptionClass, exceptionClass.getQualifiedName());
  }

  private static void addException(PsiMethod method, PsiClass exceptionClass, String exceptionName) throws IncorrectOperationException {
    PsiReferenceList throwsList = method.getThrowsList();
    PsiJavaCodeReferenceElement[] refs = throwsList.getReferenceElements();
    for (int i = 0; i < refs.length; i++) {
      PsiJavaCodeReferenceElement ref = refs[i];
      if (ref.isReferenceTo(exceptionClass)) return;
      PsiClass aClass = (PsiClass) ref.resolve();
      if (exceptionClass != null && aClass != null) {
        if (aClass.isInheritor(exceptionClass, true)) {
          PsiElementFactory factory = method.getManager().getElementFactory();
          PsiJavaCodeReferenceElement ref1;
          if (exceptionName != null) {
            ref1 = factory.createReferenceElementByFQClassName(exceptionName, method.getResolveScope());
          } else {
            PsiClassType type = factory.createType(exceptionClass);
            ref1 = factory.createReferenceElementByType(type);
          }
          ref.replace(ref1);
          return;
        }
        else if (exceptionClass.isInheritor(aClass, true)) {
            return;
          }
      }
    }

    PsiElementFactory factory = method.getManager().getElementFactory();
    PsiJavaCodeReferenceElement ref;
    if (exceptionName != null) {
      ref = factory.createReferenceElementByFQClassName(exceptionName, method.getResolveScope());
    } else {
      PsiClassType type = factory.createType(exceptionClass);
      ref = factory.createReferenceElementByType(type);
    }
    throwsList.add(ref);
  }

  // todo: move to PsiThrowsList?
  public static void removeException(PsiMethod method, String exceptionClass) throws IncorrectOperationException {
    PsiJavaCodeReferenceElement[] refs = method.getThrowsList().getReferenceElements();
    for (int i = 0; i < refs.length; i++) {
      PsiJavaCodeReferenceElement ref = refs[i];
      if (ref.getCanonicalText().equals(exceptionClass)) {
        ref.delete();
      }
    }
  }

  public static boolean isVariableNameUnique(String name, PsiElement place) {
    PsiResolveHelper helper = place.getManager().getResolveHelper();
    PsiVariable refVar = helper.resolveReferencedVariable(name, place);
    if (refVar != null) return false;
    return true;
  }

  /**
   * @deprecated Use {@link PsiManager#isInProject(com.intellij.psi.PsiElement)}
   */
  public static boolean isInProject(PsiElement element) {
    return element.getManager().isInProject(element);
  }

  public static void updatePackageStatement(PsiFile file) throws IncorrectOperationException {
    if (!(file instanceof PsiJavaFile)) return;

    PsiManager manager = file.getManager();
    PsiElementFactory factory = manager.getElementFactory();
    PsiDirectory dir = file.getContainingDirectory();
    PsiPackage aPackage = dir.getPackage();
    if (aPackage == null) return;
    String packageName = aPackage.getQualifiedName();
    PsiPackageStatement statement = ((PsiJavaFile) file).getPackageStatement();
    if (statement != null) {
      if (packageName.length() > 0) {
        statement.getPackageReference().bindToElement(aPackage);
      }
      else {
        statement.delete();
      }
    }
    else {
      if (packageName.length() > 0) {
        String text = "package " + packageName + ";";
        String ext = StdFileTypes.JAVA.getDefaultExtension();
        PsiJavaFile dummyFile = (PsiJavaFile) factory.createFileFromText("_Dummy_." + ext, text);
        statement = dummyFile.getPackageStatement();
        if (statement == null) {
          throw new IncorrectOperationException();
        }
        file.add(statement);
      }
    }
  }

  /**
   * @return enclosing outermost (method or class initializer) body but not higher than scope
   */
  public static PsiElement getTopLevelEnclosingCodeBlock(PsiElement element, PsiElement scope) {
    PsiElement blockSoFar = null;
    while (element != null) {
      // variable can be defined in for loop initializer
      if (element instanceof PsiCodeBlock || element instanceof PsiForStatement || element instanceof PsiForeachStatement) {
        blockSoFar = element;
      }
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiMethod
          && parent.getParent() instanceof PsiClass
          && !isLocalOrAnonymousClass((PsiClass)parent.getParent()))
        break;
      if (parent instanceof PsiClassInitializer && !(parent.getParent() instanceof PsiAnonymousClass)) break;
      if (parent instanceof PsiField && ((PsiField) parent).getInitializer() == element) {
        blockSoFar = element;
      }
      if (element instanceof PsiClass && !isLocalOrAnonymousClass((PsiClass)element)) {
        break;
      }
      if (element instanceof JspFile) {
        return element;
      }
      if (element == scope) break;
      element = parent;
    }
    return blockSoFar;
  }

  public static boolean isLocalOrAnonymousClass(PsiClass psiClass) {
    return psiClass instanceof PsiAnonymousClass || isLocalClass(psiClass);
  }

  public static boolean isLocalClass(PsiClass psiClass) {
    PsiElement parent = psiClass.getParent();
    return parent instanceof PsiDeclarationStatement && parent.getParent() instanceof PsiCodeBlock;
  }

  /**
   * @return codeblock topmost codeblock where variable makes sense
   */
  public static PsiElement getVariableCodeBlock(PsiVariable variable, PsiElement context) {
    PsiElement codeBlock = null;
    if (variable instanceof PsiParameter) {
      PsiElement declarationScope = ((PsiParameter)variable).getDeclarationScope();
      if (declarationScope instanceof PsiTryStatement) {
        PsiElement element = variable;
        while (element != null) {
          if (element instanceof PsiCodeBlock) {
            codeBlock = element;
            break;
          }
          element = element.getNextSibling();
        }
      }
      else if (declarationScope instanceof PsiForeachStatement) {
        codeBlock = (((PsiForeachStatement)declarationScope)).getBody();
      }
      else if (declarationScope instanceof PsiMethod) {
        codeBlock = ((PsiMethod)declarationScope).getBody();
      }
    }
    else if (variable instanceof PsiLocalVariable && variable.getParent() instanceof PsiForStatement) {
      return variable.getParent();
    }
    else if (variable instanceof PsiField && context != null) {
      final PsiClass aClass = ((PsiField) variable).getContainingClass();
      while (context != null && context.getParent() != aClass) {
        context = context.getParent();
      }
      return context instanceof PsiMethod ?
          ((PsiMethod) context).getBody() :
          context instanceof PsiClassInitializer ? ((PsiClassInitializer) context).getBody() : null;
    }
    else {
      final PsiElement scope = variable.getParent() == null ? null : variable.getParent().getParent();
      codeBlock = getTopLevelEnclosingCodeBlock(variable, scope);
      if (codeBlock != null && codeBlock.getParent() instanceof PsiSwitchStatement) codeBlock = codeBlock.getParent().getParent();
    }
    return codeBlock;
  }

  public static boolean isIncrementDecrementOperation(PsiElement element) {
    if (element instanceof PsiPostfixExpression) {
      final IElementType sign = ((PsiPostfixExpression) element).getOperationSign().getTokenType();
      if (sign == JavaTokenType.PLUSPLUS || sign == JavaTokenType.MINUSMINUS)
        return true;
    }
    else if (element instanceof PsiPrefixExpression) {
      final IElementType sign = ((PsiPrefixExpression) element).getOperationSign().getTokenType();
      if (sign == JavaTokenType.PLUSPLUS || sign == JavaTokenType.MINUSMINUS)
        return true;
    }
    return false;
  }

  public static int getAccessLevel(PsiModifierList modifierList) {
    if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
      return ACCESS_LEVEL_PRIVATE;
    }
    else if (modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      return ACCESS_LEVEL_PACKAGE_LOCAL;
    }
    else if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
      return ACCESS_LEVEL_PROTECTED;
    }
    else {
      return ACCESS_LEVEL_PUBLIC;
    }
  }

  public static String getAccessModifier(int accessLevel) {
    return accessLevel > accessModifiers.length ? null : accessModifiers[accessLevel - 1];
  }

  private static final String[] accessModifiers = new String[]{
    PsiModifier.PRIVATE, PsiModifier.PACKAGE_LOCAL, PsiModifier.PROTECTED, PsiModifier.PUBLIC
  };

  public static PsiFile findRelativeFile(String uri, PsiElement base) {
    if (base instanceof PsiFile) {
      PsiFile baseFile = (PsiFile) base;
      if (baseFile.getOriginalFile() != null) return findRelativeFile(uri, baseFile.getOriginalFile());
      VirtualFile file = VfsUtil.findRelativeFile(uri, baseFile.getVirtualFile());
      if (file == null) return null;
      return base.getManager().findFile(file);
    }
    else if (base instanceof PsiDirectory) {
      PsiDirectory baseDir = (PsiDirectory) base;
      VirtualFile file = VfsUtil.findRelativeFile(uri, baseDir.getVirtualFile());
      if (file == null) return null;
      return base.getManager().findFile(file);
    }

    return null;
  }

  public static PsiDirectory findRelativeDirectory(String uri, PsiElement base) {
    if (base instanceof PsiFile) {
      PsiFile baseFile = (PsiFile) base;
      if (baseFile.getOriginalFile() != null) return findRelativeDirectory(uri, baseFile.getOriginalFile());
      VirtualFile file = VfsUtil.findRelativeFile(uri, baseFile.getVirtualFile());
      if (file == null) return null;
      return base.getManager().findDirectory(file);
    }
    else if (base instanceof PsiDirectory) {
      PsiDirectory baseDir = (PsiDirectory) base;
      VirtualFile file = VfsUtil.findRelativeFile(uri, baseDir.getVirtualFile());
      if (file == null) return null;
      return base.getManager().findDirectory(file);
    }

    return null;
  }

  /**
   * @return true if element specified is statement or expression statement. see JLS 14.5-14.8
   */
  public static boolean isStatement(PsiElement element) {
    PsiElement parent = element.getParent();

    if (element instanceof PsiExpressionListStatement) {
      // statement list allowed in for() init or update only
      if (!(parent instanceof PsiForStatement)) return false;
      final PsiForStatement forStatement = (PsiForStatement)parent;
      if (!(element == forStatement.getInitialization() || element == forStatement.getUpdate())) return false;
      final PsiExpressionList expressionList = ((PsiExpressionListStatement) element).getExpressionList();
      final PsiExpression[] expressions = expressionList.getExpressions();
      for (int i = 0; i < expressions.length; i++) {
        PsiExpression expression = expressions[i];
        if (!isStatement(expression)) return false;
      }
      return true;
    }
    else if (element instanceof PsiExpressionStatement) {
      final PsiExpression expression = ((PsiExpressionStatement) element).getExpression();
      if (expression == null) return false;
      return isStatement(expression);
    }
    if (element instanceof PsiDeclarationStatement) {
      if (parent instanceof PsiCodeBlock || parent instanceof JspElement) return true;
      if (parent instanceof PsiCodeFragment) return true;

      if (!(parent instanceof PsiForStatement) || ((PsiForStatement)parent).getBody() == element) {
        return false;
      }
    }

    if (element instanceof PsiStatement) return true;
    if (element instanceof PsiAssignmentExpression) return true;
    if (isIncrementDecrementOperation(element)) return true;
    if (element instanceof PsiMethodCallExpression) return true;
    if (element instanceof PsiNewExpression) {
      return !(((PsiNewExpression) element).getType() instanceof PsiArrayType);
    }
    if (element instanceof PsiCodeBlock) return true;
    return false;
  }

  public static PsiElement getEnclosingStatement(PsiElement element) {
    while (element != null) {
      if (element.getParent() instanceof PsiCodeBlock) return element;
      element = element.getParent();
    }
    return null;
  }


  public static PsiElement getElementInclusiveRange(PsiElement scope, TextRange range) {
    PsiElement psiElement = scope.findElementAt(range.getStartOffset());
    while (!psiElement.getTextRange().contains(range)) {
      if (psiElement == scope) return null;
      psiElement = psiElement.getParent();
    }
    return psiElement;
  }

  private static final Key<Pattern> REGEXP_IN_TYPE_NAME_PATTERN = Key.create("REGEXP_IN_TYPE_NAME_PATTERN");

  public static Pattern convertToRegexp(PsiIdPattern idPattern) {
    Pattern result = idPattern.getUserData(REGEXP_IN_TYPE_NAME_PATTERN);
    if (result == null) {
      StringBuffer buf = new StringBuffer();
      convertToRegexp(idPattern, buf);
      result = Pattern.compile(buf.toString());
      idPattern.putUserData(REGEXP_IN_TYPE_NAME_PATTERN, result);
    }
    return result;
  }

  public static Pattern convertToRegexp(PsiTypeNamePattern typeNamePattern) {
    Pattern result = typeNamePattern.getUserData(REGEXP_IN_TYPE_NAME_PATTERN);

    if (result == null) {
      StringBuffer regexp;
      PsiTypeNamePatternElement[] namePatternElements = typeNamePattern.getNamePatternElements();
      regexp = new StringBuffer();
      boolean doubleDot = false;
      for (int i = 0; i < namePatternElements.length; i++) {
        if (i > 0 && !doubleDot) {
          regexp.append("\\."); // dot
        }

        PsiTypeNamePatternElement typePatternElement = namePatternElements[i];
        PsiIdPattern pattern = typePatternElement.getPattern();
        if (pattern != null) {
          convertToRegexp(pattern, regexp);
          doubleDot = false;
        }
        else {
          regexp.append("(.*\\.)?"); // Empty string or any string that ends up with dot.
          doubleDot = true;
        }
      }

      result = Pattern.compile(regexp.toString());
      typeNamePattern.putUserData(REGEXP_IN_TYPE_NAME_PATTERN, result);
    }

    return result;
  }

  private static void convertToRegexp(PsiIdPattern pattern, StringBuffer regexp) {
    final String canonicalText = pattern.getCanonicalText();
    for (int j = 0; j < canonicalText.length(); j++) {
      final char c = canonicalText.charAt(j);
      if (c == '*') {
        regexp.append("[^\\.]*");
      }
      else {
        regexp.append(c);
      }
    }
  }

  public static PsiClass resolveClassInType(PsiType type) {
    if (type instanceof PsiClassType) {
      return ((PsiClassType) type).resolve();
    }
    if (type instanceof PsiArrayType) {
      return resolveClassInType(((PsiArrayType) type).getComponentType());
    }
    return null;
  }

  public static PsiClassType.ClassResolveResult resolveGenericsClassInType(PsiType type) {
    if (type instanceof PsiClassType) {
      final PsiClassType classType = (PsiClassType) type;
      return classType.resolveGenerics();
    }
    if (type instanceof PsiArrayType) {
      return resolveGenericsClassInType(((PsiArrayType) type).getComponentType());
    }
    return PsiClassType.ClassResolveResult.EMPTY;
  }

  public static PsiType convertAnonymousToBaseType(PsiType type) {
    PsiClass psiClass = resolveClassInType(type);
    if (psiClass instanceof PsiAnonymousClass) {
      int dims = type.getArrayDimensions();
      type = ((PsiAnonymousClass) psiClass).getBaseClassType();
      while (dims != 0) {
        type = type.createArrayType();
        dims--;
      }
    }
    return type;
  }

  /** @return name for element using element structure info
   * TODO: Extend functionality for XML/JSP
   */
  public static String getName(PsiElement element) {
    String name = null;
    if (element instanceof PsiMetaOwner) {
      final PsiMetaData data = ((PsiMetaOwner) element).getMetaData();
      if (data != null)
        name = data.getName(element);
    }
    if (name == null && element instanceof PsiNamedElement) {
      name = ((PsiNamedElement) element).getName();
    }
    return name;
  }

  public static boolean isApplicable(PsiMethod method, PsiSubstitutor substitutorForMethod, PsiExpressionList argList) {
    PsiExpression[] args = argList == null ? PsiExpression.EMPTY_ARRAY : argList.getExpressions();
    final PsiParameter[] parms = method.getParameterList().getParameters();

    if (!method.isVarArgs()) {
      if (args == null || args.length != parms.length) {
        return false;
      }

      for (int i = 0; i < args.length; i++) {
        final PsiExpression arg = args[i];
        final PsiType type = arg.getType();
        if (type == null) return false; //?
        final PsiType parmType = parms[i].getType();
        final PsiType substitutedParmType = substitutorForMethod.substituteAndCapture(parmType);
        if (!TypeConversionUtil.isAssignable(substitutedParmType, type)) {
          return false;
        }
      }
    } else {
      if (args == null || args.length < parms.length - 1) {
        return false;
      }

      PsiParameter lastParameter = parms[parms.length - 1];
      PsiType lastParmType = lastParameter.getType();
      if (!(lastParmType instanceof PsiArrayType)) return false;
      lastParmType = substitutorForMethod.substituteAndCapture(lastParmType);

      if (lastParameter.isVarArgs()) {
        for (int i = 0; i < parms.length - 1; i++) {
          PsiParameter parm = parms[i];
          if (parm.isVarArgs()) return false;

          final PsiExpression arg = args[i];
          final PsiType argType = arg.getType();
          if (argType == null) return false;
          final PsiType parmType = parms[i].getType();
          final PsiType substitutedParmType = substitutorForMethod.substituteAndCapture(parmType);
          if (!TypeConversionUtil.isAssignable(substitutedParmType, argType)) {
            return false;
          }
        }

        if (args.length == parms.length) { //call with array as vararg parameter
          PsiType lastArgType = args[args.length - 1].getType();
          if (lastArgType != null && TypeConversionUtil.isAssignable(lastParmType, lastArgType)) return true;
        }

        lastParmType = ((PsiArrayType)lastParmType).getComponentType();
        for (int i = parms.length - 1; i < args.length; i++) {
          PsiType argType = args[i].getType();
          if (argType == null || !TypeConversionUtil.isAssignable(lastParmType, argType)) {
            return false;
          }
        }
      }
      else {
        return false;
      }
    }

    return true;
  }

  public static boolean equalOnClass(PsiSubstitutor s1, PsiSubstitutor s2, PsiClass aClass) {
    return equalOnEquivalentClasses(s1, aClass, s2, aClass);
  }

  public static boolean equalOnEquivalentClasses(PsiSubstitutor s1, PsiClass aClass, PsiSubstitutor s2, PsiClass bClass) {
    // assume generic class equals to non-generic
    if (aClass.hasTypeParameters() != bClass.hasTypeParameters()) return true;
    final PsiTypeParameterList typeParamList1 = aClass.getTypeParameterList();
    final PsiTypeParameterList typeParamList2 = bClass.getTypeParameterList();
    if (typeParamList1 == null && typeParamList2 == null) return true;
    if (typeParamList1 == null || typeParamList2 == null) return false;
    final PsiTypeParameter[] typeParameters1 = typeParamList1.getTypeParameters();
    final PsiTypeParameter[] typeParameters2 = typeParamList2.getTypeParameters();
    if (typeParameters1.length != typeParameters2.length) return false;
    for (int i = 0; i < typeParameters1.length; i++) {
      if (!Comparing.equal(s1.substitute(typeParameters1[i]), s2.substitute(typeParameters2[i]))) return false;
    }
    if (aClass.hasModifierProperty(PsiModifier.STATIC)) return true;
    final PsiClass containingClass1 = aClass.getContainingClass();
    final PsiClass containingClass2 = bClass.getContainingClass();

    if (containingClass1 != null && containingClass2 != null) {
      return equalOnEquivalentClasses(s1, containingClass1, s2, containingClass2);
    }

    return containingClass1 == null && containingClass2 == null;
  }

  /**
   * JLS 15.28
   */
  public static boolean isCompileTimeConstant(final PsiField field) {
    return field.hasModifierProperty(PsiModifier.FINAL)
        && (TypeConversionUtil.isPrimitiveAndNotNull(field.getType()) || field.getType().equalsToText("java.lang.String"))
        && field.hasInitializer()
        && isConstantExpression(field.getInitializer());
  }

  public static boolean allMethodsHaveSameSignature(PsiMethod[] methods) {
    if (methods.length == 0) return true;
    final MethodSignature methodSignature = methods[0].getSignature(PsiSubstitutor.EMPTY);
    for (int i = 1; i < methods.length; i++) {
      PsiMethod method = methods[i];
      if (!methodSignature.equals(method.getSignature(PsiSubstitutor.EMPTY))) return false;
    }
    return true;
  }

    public static PsiExpression deparenthesizeExpression(PsiExpression rExpression) {
      if (rExpression instanceof PsiParenthesizedExpression) {
        return deparenthesizeExpression(
          ((PsiParenthesizedExpression)rExpression).getExpression());
      }
      if (rExpression instanceof PsiTypeCastExpression) {
        return deparenthesizeExpression(
          ((PsiTypeCastExpression)rExpression).getOperand());
      }
      return rExpression;
    }

  /**
   * Checks whether given class is inner (as opposed to nested)
   *
   * @param aClass
   * @return
   */
  public static boolean isInnerClass(PsiClass aClass) {
    return !aClass.hasModifierProperty(PsiModifier.STATIC) &&
           (aClass.getParent() instanceof PsiClass || aClass.getParent() instanceof JspFile);
  }

  public static PsiElement findModifierInList(final PsiModifierList modifierList, String modifier) {
    final PsiElement[] children = modifierList.getChildren();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];
      if (child.getText().equals(modifier)) return child;
    }
    return null;
  }

  public static boolean isLoopStatement(PsiElement element) {
    return element instanceof PsiWhileStatement
           || element instanceof PsiForStatement
           || element instanceof PsiDoWhileStatement
           || element instanceof PsiForeachStatement;
  }

  private static class TypeParameterIterator implements Iterator<PsiTypeParameter> {
    private int myIndex;
    private PsiTypeParameterListOwner myCurrentOwner;
    private boolean myNextObtained;
    private PsiTypeParameter[] myCurrentParams;

    private PsiTypeParameter myNext;

      private TypeParameterIterator(PsiTypeParameterListOwner owner) {
      myCurrentOwner = owner;
      obtainCurrentParams(owner);
      myNextObtained = false;
    }

    private void obtainCurrentParams(PsiTypeParameterListOwner owner) {
      final PsiTypeParameterList typeParameterList = owner.getTypeParameterList();
      if (typeParameterList != null) {
        myCurrentParams = typeParameterList.getTypeParameters();
      }
      else {
        myCurrentParams = PsiTypeParameter.EMPTY_ARRAY;
      }
      myIndex = myCurrentParams.length - 1;
    }

    public boolean hasNext() {
      nextElement();
      return myNext != null;
    }

    public PsiTypeParameter next() {
      nextElement();
      if (myNext == null) throw new NoSuchElementException();
      myNextObtained = false;
      return myNext;
    }

    public void remove() {
      throw new UnsupportedOperationException("TypeParameterIterator.remove");
    }

    private void nextElement() {
      if (myNextObtained) return;
      if (myIndex >= 0) {
        myNext = myCurrentParams[myIndex--];
        myNextObtained = true;
        return;
      }
      if (myCurrentOwner.hasModifierProperty(PsiModifier.STATIC) || myCurrentOwner.getContainingClass() == null) {
        myNext = null;
        myNextObtained = true;
        return;
      }
      myCurrentOwner = myCurrentOwner.getContainingClass();
      obtainCurrentParams(myCurrentOwner);
      nextElement();
    }
  }

  /**
   * Returns iterator of type parameters visible in owner. Type parameters are iterated in
   * inner-to-outer, right-to-left order.
   * @param owner
   * @return
   */
  public static Iterator<PsiTypeParameter> typeParametersIterator(PsiTypeParameterListOwner owner) {
    return new TypeParameterIterator(owner);
  }

  public static boolean canBeOverriden(PsiMethod method) {
    PsiClass parentClass = method.getContainingClass();
    if (parentClass == null) return false;
    if (method.isConstructor()) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (method.hasModifierProperty(PsiModifier.FINAL)) return false;
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    if (parentClass instanceof PsiAnonymousClass) return false;
    if (parentClass.hasModifierProperty(PsiModifier.FINAL)) return false;
    return true;
  }

  public static PsiElement[] mapElements(CandidateInfo[] candidates) {
    PsiElement[] result = new PsiElement[candidates.length];
    for (int i = 0; i < candidates.length; i++) {
      result[i] = candidates[i].getElement();
    }
    return result;
  }

  public static boolean hasErrorElementChild(PsiElement element) {
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiErrorElement) return true;
    }
    return false;
  }

  public static PsiMember findEnclosingConstructorOrInitializer(PsiElement expression) {
    PsiElement parent = PsiTreeUtil.getParentOfType(expression, new Class[]{PsiClassInitializer.class, PsiMethod.class});
    if (parent instanceof PsiMethod && !((PsiMethod)parent).isConstructor()) return null;
    return (PsiMember)parent;
  }

  public static boolean checkName(PsiElement element, String name) {
    if (element instanceof PsiMetaOwner) {
      final PsiMetaData data = ((PsiMetaOwner) element).getMetaData();
      if (data != null) return name.equals(data.getName(element));
    }
    if (element instanceof PsiNamedElement) {
      return name.equals(((PsiNamedElement) element).getName());
    }
    return false;
  }

  public static boolean isInCovariantPosition(PsiExpression expression) {
    return isAccessedForWriting(expression) ||
           isArrayExpressionInSelector(expression);
  }

  private static boolean isArrayExpressionInSelector(PsiExpression expression) {
    return expression.getParent() instanceof PsiArrayAccessExpression &&
           ((PsiArrayAccessExpression)expression.getParent()).getArrayExpression() == expression;
  }

  public static boolean isRawSubstitutorForClass (PsiClass aClass, PsiSubstitutor substitutor) {
    final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(aClass);
    while (iterator.hasNext()) {
      PsiTypeParameter parameter = iterator.next();
      if (substitutor.substitute(parameter) == null) return true;
    }
    return false;
  }

  public static PsiType getRawType(PsiType type) {
    if (type instanceof PsiClassType) {
      return ((PsiClassType)type).rawType();
    }
    return type;
  }

  public static boolean isUnderPsiRoot(PsiFile root, PsiElement element) {
    PsiElement[] psiRoots = root.getPsiRoots();
    for (int i = 0; i < psiRoots.length; i++) {
      PsiElement psiRoot = psiRoots[i];
      if (PsiTreeUtil.isAncestor(psiRoot, element, false)) return true;
    }
    return false;
  }
}
