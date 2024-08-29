// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaFileCodeStyleFacade;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.templateLanguages.TemplateLanguageUtil;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaDocUtil {
  private static final Logger LOG = Logger.getInstance(JavaDocUtil.class);

  private static final @NonNls Pattern ourTypePattern = Pattern.compile("[ ]+[^ ^\\[^\\]]");
  private static final String JAVA_LANG = "java.lang.";

  private JavaDocUtil() {
  }

  public static @Nullable PsiClass resolveClassInTagValue(@Nullable PsiDocTagValue value) {
    if (value == null) return null;
    PsiElement refHolder = value.getFirstChild();
    if (refHolder != null) {
      PsiElement refElement = refHolder.getFirstChild();
      if (refElement instanceof PsiJavaCodeReferenceElement) {
        PsiElement target = ((PsiJavaCodeReferenceElement)refElement).resolve();
        if (target instanceof PsiClass) {
          return (PsiClass)target;
        }
      }
    }

    return null;
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

  public static @Nullable PsiElement findReferenceTarget(@NotNull PsiManager manager, @NotNull String refText, PsiElement context) {
    return findReferenceTarget(manager, refText, context, true);
  }

  public static @Nullable PsiElement findReferenceTarget(@NotNull PsiManager manager, @NotNull String refText, PsiElement context, boolean useNavigationElement) {
    LOG.assertTrue(context == null || context.isValid());
    if (context != null) {
      context = context.getNavigationElement();
    }

    int poundIndex = refText.indexOf('#');
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    if (poundIndex < 0) {
      PsiClass aClass = findClassFromRef(manager, facade, refText, context);

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
        PsiClass aClass = findClassFromRef(manager, facade, classRef, context);

        if (aClass == null) return null;
        PsiElement member = findReferencedMember(aClass, refText.substring(poundIndex + 1), context);
        return useNavigationElement && member != null ? member.getNavigationElement() : member;
      }
      else {
        String memberRefText = refText.substring(1);
        PsiElement scope = context;
        while (scope != null && !(scope instanceof PsiFile)) {
          if (scope instanceof PsiClass) {
            PsiElement member = findReferencedMember((PsiClass)scope, memberRefText, context);
            if (member != null) {
              return useNavigationElement ? member.getNavigationElement() : member;
            }
          }
          scope = scope.getParent();
        }
        return null;
      }
    }
  }

  private static PsiClass findClassFromRef(@NotNull PsiManager manager,
                                           @NotNull JavaPsiFacade facade,
                                           @NotNull String refText, PsiElement context) {
    PsiClass aClass = facade.getResolveHelper().resolveReferencedClass(refText, context);

    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
    if (aClass == null) aClass = facade.findClass(refText, projectScope);
    if (aClass == null && refText.indexOf('.') == -1 && context != null) {
      // find short-named class in the same package (maybe in the different module)
      PsiFile file = context.getContainingFile();
      PsiDirectory directory = file == null ? null : file.getContainingDirectory();
      PsiPackage aPackage = directory == null ? null : JavaDirectoryService.getInstance().getPackage(directory);
      aClass = aPackage == null ? null : ArrayUtil.getFirstElement(aPackage.findClassByShortName(refText, projectScope));
    }
    return aClass;
  }

  private static @Nullable PsiElement findReferencedMember(@NotNull PsiClass aClass, @NotNull String memberRefText, PsiElement context) {
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
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(aClass.getProject());
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

      MethodSignature methodSignature = MethodSignatureUtil.createMethodSignature(name, types, PsiTypeParameter.EMPTY_ARRAY,
                                                                                  PsiSubstitutor.EMPTY, name.equals(aClass.getName()));

      final PsiMethod[] allMethods;
      if (context != null) {
        allMethods = PsiDocMethodOrFieldRef.getAllMethods(aClass, context);
      }
      else {
        allMethods = aClass.findMethodsByName(name, true);
      }

      PsiMethod[] methods = PsiDocMethodOrFieldRef.findMethods(methodSignature, aClass, name, allMethods);

      if (methods.length == 0) return null;

      PsiMethod found = methods[0];

      int hashIndex = memberRefText.indexOf('#', rparenIndex);
      if (hashIndex != -1) {
        PsiParameter[] params = found.getParameterList().getParameters();
        int parameterNumber = Integer.parseInt(memberRefText.substring(hashIndex + 1));
        if (parameterNumber < params.length) return params[parameterNumber];
      }
      return found;
    }
  }

  public static @Nullable String getReferenceText(Project project, PsiElement element) {
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
      StringBuilder buffer = new StringBuilder();
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        buffer.append(getReferenceText(project, aClass));
      }
      buffer.append("#");
      buffer.append(name);
      buffer.append("(");
      PsiParameter[] parms = method.getParameterList().getParameters();
      boolean spaceBeforeComma = JavaFileCodeStyleFacade.forContext(element.getContainingFile()).isSpaceBeforeComma();
      boolean spaceAfterComma = JavaFileCodeStyleFacade.forContext(element.getContainingFile()).isSpaceAfterComma();
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
    else if (element instanceof PsiNamedElement) {
      return ((PsiNamedElement)element).getName();
    }

    return null;
  }

  public static @NlsSafe String getShortestClassName(PsiClass aClass, PsiElement context) {
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
    if (manager.areElementsEquivalent(aClass, resolvedClass)) {
      return shortName;
    }
    return JAVA_LANG.length() + shortName.length() == qName.length() ? StringUtil.trimStart(qName, JAVA_LANG) : qName;
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
          while (scope != null && !(scope instanceof PsiFile)) {
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
    StringBuilder buffer = new StringBuilder();
    boolean spaceBeforeComma = JavaFileCodeStyleFacade.forContext(context.getContainingFile()).isSpaceBeforeComma();
    boolean spaceAfterComma = JavaFileCodeStyleFacade.forContext(context.getContainingFile()).isSpaceAfterComma();
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
    return memberText.substring(0, parenthIndex + 1) + buffer + ")";
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

  public static boolean isDanglingDocComment(@NotNull PsiDocComment comment, boolean ignoreCopyright) {
    if (comment.getOwner() != null || TemplateLanguageUtil.isInsideTemplateFile(comment)) {
      return false;
    }
    if (isInsidePackageInfo(comment) &&
        PsiTreeUtil.skipWhitespacesAndCommentsForward(comment) instanceof PsiPackageStatement &&
        "package-info.java".equals(comment.getContainingFile().getName())) {
      return false;
    }
    if (ignoreCopyright && comment.getPrevSibling() == null && comment.getParent() instanceof PsiFile) {
      return false;
    }
    return true;
  }
}
