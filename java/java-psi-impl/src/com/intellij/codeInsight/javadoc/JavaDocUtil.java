/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.javadoc;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaDocUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.javadoc.JavaDocUtil");

  @NonNls private static final Pattern ourTypePattern = Pattern.compile("[ ]+[^ ^\\[^\\]]");

  private JavaDocUtil() {
  }

  /**
   * Extracts a reference to a source element from the beginning of the text.
   * 
   * @return length of the extracted reference
   */
  public static int extractReference(String text) {
    int lparenthIndex = text.indexOf('(');
    int spaceIndex = text.indexOf(' ');
    if (spaceIndex < 0) {
      spaceIndex = text.length();
    }
    if (lparenthIndex < 0) {
      return spaceIndex;
    }
    else {
      if (spaceIndex < lparenthIndex) {
        return spaceIndex;
      }
      int rparenthIndex = text.indexOf(')', lparenthIndex);
      if (rparenthIndex < 0) {
        rparenthIndex = text.length() - 1;
      }
      return rparenthIndex + 1;
    }
  }

  @Nullable
  public static PsiElement findReferenceTarget(PsiManager manager, String refText, PsiElement context) {
    return findReferenceTarget(manager, refText, context, true);
  }

  @Nullable
  public static PsiElement findReferenceTarget(PsiManager manager, String refText, PsiElement context, boolean useNavigationElement) {
    LOG.assertTrue(context == null || context.isValid());
    if (context != null) {
      context = context.getNavigationElement();
    }

    int poundIndex = refText.indexOf('#');
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    if (poundIndex < 0) {
      PsiClass aClass = facade.getResolveHelper().resolveReferencedClass(refText, context);

      if (aClass == null) aClass = facade.findClass(refText, context.getResolveScope());

      if (aClass != null) {
        return useNavigationElement ? aClass.getNavigationElement() : aClass;
      }
      PsiPackage aPackage = facade.findPackage(refText);
      if (aPackage!=null) return aPackage;
      return null;
    }
    else {
      String classRef = refText.substring(0, poundIndex).trim();
      if (!classRef.isEmpty()) {
        PsiClass aClass = facade.getResolveHelper().resolveReferencedClass(classRef, context);

        if (aClass == null) aClass = facade.findClass(classRef, context.getResolveScope());

        if (aClass == null) return null;
        PsiElement member = findReferencedMember(aClass, refText.substring(poundIndex + 1), context);
        return useNavigationElement && member != null ? member.getNavigationElement() : member;
      }
      else {
        String memberRefText = refText.substring(1);
        PsiElement scope = context;
        while (true) {
          if (scope instanceof PsiFile) break;
          if (scope instanceof PsiClass) {
            PsiElement member = findReferencedMember((PsiClass)scope, memberRefText, context);
            if (member != null) {
              return useNavigationElement ? member.getNavigationElement() :  member;
            }
          }
          scope = scope.getParent();
        }
        return null;
      }
    }
  }

  @Nullable
  private static PsiElement findReferencedMember(PsiClass aClass, String memberRefText, PsiElement context) {
    int parenthIndex = memberRefText.indexOf('(');
    if (parenthIndex < 0) {
      String name = memberRefText;
      PsiField field = aClass.findFieldByName(name, true);
      if (field != null) return field;
      PsiClass inner = aClass.findInnerClassByName(name, true);
      if (inner != null) return inner;
      PsiMethod[] methods = aClass.getAllMethods();
      for (PsiMethod method : methods) {
        if (method.getName().equals(name)) return method;
      }
      return null;
    }
    else {
      String name = memberRefText.substring(0, parenthIndex).trim();
      int rparenIndex = memberRefText.lastIndexOf(')');
      if (rparenIndex == -1) return null;
      
      String parmsText = memberRefText.substring(parenthIndex + 1, rparenIndex).trim();
      StringTokenizer tokenizer = new StringTokenizer(parmsText.replaceAll("[*]", ""), ",");
      PsiType[] types = PsiType.createArray(tokenizer.countTokens());
      int i = 0;
      PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
      while (tokenizer.hasMoreTokens()) {
        String parmText = tokenizer.nextToken().trim();
        try {
          Matcher typeMatcher = ourTypePattern.matcher(parmText);
          String typeText = parmText;

          if (typeMatcher.find()) {
            typeText = parmText.substring(0, typeMatcher.start());
          }

          PsiType type = factory.createTypeFromText(typeText, context);
          types[i++] = type;
        }
        catch (IncorrectOperationException e) {
          LOG.info(e);
        }
      }
      PsiMethod[] methods = aClass.findMethodsByName(name, true);
      MethodsLoop:
      for (PsiMethod method : methods) {
        PsiParameter[] parms = method.getParameterList().getParameters();
        if (parms.length != types.length) continue;

        for (int k = 0; k < parms.length; k++) {
          PsiParameter parm = parms[k];
          final PsiType parmType = parm.getType();
          if (
            types[k] != null &&
            !TypeConversionUtil.erasure(parmType).getCanonicalText().equals(types[k].getCanonicalText()) &&
            !parmType.getCanonicalText().equals(types[k].getCanonicalText()) &&
            !TypeConversionUtil.isAssignable(parmType, types[k])
            ) {
            continue MethodsLoop;
          }
        }

        int hashIndex = memberRefText.indexOf('#',rparenIndex);
        if (hashIndex != -1) {
          int parameterNumber = Integer.parseInt(memberRefText.substring(hashIndex + 1));
          if (parameterNumber < parms.length) return method.getParameterList().getParameters()[parameterNumber];
        }
        return method;
      }
      return null;
    }
  }

  @Nullable
  public static String getReferenceText(Project project, PsiElement element) {
    if (element instanceof PsiPackage) {
      return ((PsiPackage)element).getQualifiedName();
    }
    else if (element instanceof PsiClass) {
      final String refText = ((PsiClass)element).getQualifiedName();
      if (refText != null) return refText;
      return ((PsiClass)element).getName();
    }
    else if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      String name = field.getName();
      PsiClass aClass = field.getContainingClass();
      if (aClass != null) {
        return getReferenceText(project, aClass) + "#" + name;
      }
      else {
        return "#" + name;
      }
    }
    else if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      String name = method.getName();
      StringBuffer buffer = new StringBuffer();
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        buffer.append(getReferenceText(project, aClass));
      }
      buffer.append("#");
      buffer.append(name);
      buffer.append("(");
      PsiParameter[] parms = method.getParameterList().getParameters();
      boolean spaceBeforeComma = JavaDocCodeStyle.getInstance(project).spaceBeforeComma();
      boolean spaceAfterComma = JavaDocCodeStyle.getInstance(project).spaceAfterComma();
      for (int i = 0; i < parms.length; i++) {
        PsiParameter parm = parms[i];
        String typeText = TypeConversionUtil.erasure(parm.getType()).getCanonicalText();
        buffer.append(typeText);
        if (i < parms.length - 1) {
          if (spaceBeforeComma) {
            buffer.append(" ");
          }
          buffer.append(",");
          if (spaceAfterComma) {
            buffer.append(" ");
          }
        }
      }
      buffer.append(")");
      return buffer.toString();
    }
    else if (element instanceof PsiParameter) {
      final PsiElement method = ((PsiParameter)element).getDeclarationScope();
      if (method instanceof PsiMethod) {
        return getReferenceText(project, method) +
               "#"+
               ((PsiParameterList)element.getParent()).getParameterIndex((PsiParameter)element);
      }
    }

    return null;
  }

  public static String getShortestClassName(PsiClass aClass, PsiElement context) {
    @NonNls String shortName = aClass.getName();
    if(shortName == null){
      shortName = "null";
    }
    PsiClass containingClass = aClass.getContainingClass();
    while (containingClass != null && containingClass.isPhysical() && !PsiUtil.isLocalOrAnonymousClass(containingClass)) {
      shortName = containingClass.getName() + "." + shortName;
      containingClass = containingClass.getContainingClass();
    }

    String qName = aClass.getQualifiedName();
    if (qName == null) return shortName;

    final PsiManager manager = aClass.getManager();
    PsiClass resolvedClass = null;
    try {
      resolvedClass = JavaPsiFacade.getInstance(manager.getProject()).getResolveHelper().resolveReferencedClass(shortName, context);
    }
    catch (IndexNotReadyException e) {
      LOG.debug(e);
    }
    return manager.areElementsEquivalent(aClass, resolvedClass)
      ? shortName
      : StringUtil.trimStart(qName, "java.lang.");
  }

  public static String getLabelText(Project project, PsiManager manager, String refText, PsiElement context) {
    PsiElement refElement = null;
    try {
      refElement = findReferenceTarget(manager, refText, context, false);
    }
    catch (IndexNotReadyException e) {
      LOG.debug(e);
    }
    if (refElement == null) {
      return refText.replaceFirst("^#", "").replaceAll("#", ".");
    }
    int poundIndex = refText.indexOf('#');
    if (poundIndex < 0) {
      if (refElement instanceof PsiClass) {
        return getShortestClassName((PsiClass)refElement, context);
      }
      else {
        return refText;
      }
    }
    else {
      PsiClass aClass = null;
      if (refElement instanceof PsiField) {
        aClass = ((PsiField)refElement).getContainingClass();
      }
      else if (refElement instanceof PsiMethod) {
        aClass = ((PsiMethod)refElement).getContainingClass();
      }
      else if (refElement instanceof PsiClass){
        return refText.replaceAll("#", ".");
      }
      if (aClass == null) return refText;
      String classRef = refText.substring(0, poundIndex).trim();
      String memberText = refText.substring(poundIndex + 1);
      String memberLabel = getMemberLabelText(project, manager, memberText, context);
      if (!classRef.isEmpty()) {
        PsiElement refClass = null;
        try {
          refClass = findReferenceTarget(manager, classRef, context);
        }
        catch (IndexNotReadyException e) {
          LOG.debug(e);
        }
        if (refClass instanceof PsiClass) {
          PsiElement scope = context;
          while (true) {
            if (scope == null || scope instanceof PsiFile) break;
            if (scope.equals(refClass)) {
              return memberLabel;
            }
            scope = scope.getParent();
          }
        }
        return getLabelText(project, manager, classRef, context) + "." + memberLabel;
      }
      else {
        return memberLabel;
      }
    }
  }

  private static String getMemberLabelText(Project project, PsiManager manager, String memberText, PsiElement context) {
    int parenthIndex = memberText.indexOf('(');
    if (parenthIndex < 0) return memberText;
    if (!StringUtil.endsWithChar(memberText, ')')) return memberText;
    String parms = memberText.substring(parenthIndex + 1, memberText.length() - 1);
    StringBuffer buffer = new StringBuffer();
    boolean spaceBeforeComma = JavaDocCodeStyle.getInstance(project).spaceBeforeComma();
    boolean spaceAfterComma = JavaDocCodeStyle.getInstance(project).spaceAfterComma();
    StringTokenizer tokenizer = new StringTokenizer(parms, ",");
    while (tokenizer.hasMoreTokens()) {
      String param = tokenizer.nextToken().trim();
      int index1 = param.indexOf('[');
      if (index1 < 0) index1 = param.length();
      int index2 = param.indexOf(' ');
      if (index2 < 0) index2 = param.length();
      int index = Math.min(index1, index2);
      String className = param.substring(0, index).trim();
      String shortClassName = getLabelText(project, manager, className, context);
      buffer.append(shortClassName);
      buffer.append(param.substring(className.length()));
      if (tokenizer.hasMoreElements()) {
        if (spaceBeforeComma) {
          buffer.append(" ");
        }
        buffer.append(",");
        if (spaceAfterComma) {
          buffer.append(" ");
        }
      }
    }
    return memberText.substring(0, parenthIndex + 1) + buffer.toString() + ")";
  }

  public static PsiClassType[] getImplementsList(PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      return new PsiClassType[]{((PsiAnonymousClass)aClass).getBaseClassType()};
    }

    PsiReferenceList list = aClass.getImplementsList();

    return list == null ? PsiClassType.EMPTY_ARRAY : list.getReferencedTypes();
  }

  public static PsiClassType[] getExtendsList(PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      return new PsiClassType[]{((PsiAnonymousClass)aClass).getBaseClassType()};
    }

    PsiReferenceList list = aClass.getExtendsList();

    return list == null ? PsiClassType.EMPTY_ARRAY : list.getReferencedTypes();
  }

  public static boolean isInsidePackageInfo(@Nullable PsiDocComment containingComment) {
    return containingComment != null && containingComment.getOwner() == null && containingComment.getParent() instanceof PsiJavaFile;
  }
}
