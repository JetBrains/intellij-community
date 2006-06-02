/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.psi.*;

/**
 *
 */
public class PsiFormatUtil {
  public static final int SHOW_NAME = 0x0001; // variable, method, class
  public static final int SHOW_TYPE = 0x0002; // variable, method
  public static final int TYPE_AFTER = 0x0004; // variable, method
  public static final int SHOW_MODIFIERS = 0x0008; // variable, method, class
  public static final int MODIFIERS_AFTER = 0x0010; // variable, method, class
  public static final int SHOW_REDUNDANT_MODIFIERS = 0x0020; // variable, method, class, modifier list
  public static final int SHOW_PACKAGE_LOCAL = 0x0040; // variable, method, class, modifier list
  public static final int SHOW_INITIALIZER = 0x0080; // variable
  public static final int SHOW_PARAMETERS = 0x0100; // method
  public static final int SHOW_THROWS = 0x0200; // method
  public static final int SHOW_EXTENDS_IMPLEMENTS = 0x0400; // class
  public static final int SHOW_FQ_NAME = 0x0800; // class, field, method
  public static final int SHOW_CONTAINING_CLASS = 0x1000; // field, method
  public static final int SHOW_FQ_CLASS_NAMES = 0x2000; // variable, method, class
  public static final int JAVADOC_MODIFIERS_ONLY = 0x4000; // field, method, class
  public static final int SHOW_ANONYMOUS_CLASS_VERBOSE = 0x8000; // class
  public static final int MAX_PARAMS_TO_SHOW = 7;

  public static String formatVariable(PsiVariable variable, int options, PsiSubstitutor substitutor){
    StringBuffer buffer = new StringBuffer();
    if ((options & SHOW_MODIFIERS) != 0 && (options & MODIFIERS_AFTER) == 0){
      buffer.append(formatModifiers(variable, options));
    }
    if ((options & SHOW_TYPE) != 0 && (options & TYPE_AFTER) == 0){
      if (buffer.length() > 0){
        buffer.append(' ');
      }
      buffer.append(formatType(variable.getType(), options, substitutor));
    }
    if (variable instanceof PsiField && (options & SHOW_CONTAINING_CLASS) != 0){
      PsiClass aClass = ((PsiField)variable).getContainingClass();
      if (aClass != null){
        if (buffer.length() > 0){
          buffer.append(' ');
        }
        String className = aClass.getName();
        if (className != null) {
          if ((options & SHOW_FQ_NAME) != 0){
            String qName = aClass.getQualifiedName();
            if (qName != null){
              buffer.append(qName);
            }
            else{
              buffer.append(className);
            }
          }
          else{
            buffer.append(className);
          }
          buffer.append('.');
        }
      }
      if ((options & SHOW_NAME) != 0){
        buffer.append(variable.getName());
      }
    }
    else{
      if ((options & SHOW_NAME) != 0){
        String name = variable.getName();
        if (name != null){
          if (buffer.length() > 0){
            buffer.append(' ');
          }
          buffer.append(name);
        }
      }
    }
    if ((options & SHOW_TYPE) != 0 && (options & TYPE_AFTER) != 0){
      if ((options & SHOW_NAME) != 0 && variable.getName() != null){
        buffer.append(':');
      }
      buffer.append(formatType(variable.getType(), options, substitutor));
    }
    if ((options & SHOW_MODIFIERS) != 0 && (options & MODIFIERS_AFTER) != 0){
      String modifiers = formatModifiers(variable, options);
      if (modifiers.length() > 0){
        if (buffer.length() > 0){
          buffer.append(' ');
        }
        buffer.append(modifiers);
      }
    }
    if ((options & SHOW_INITIALIZER) != 0){
      PsiExpression initializer = variable.getInitializer();
      if (initializer != null){
        buffer.append(" = ");
        String text = initializer.getText();
        int index1 = text.lastIndexOf('\n');
        if (index1 < 0) index1 = text.length();
        int index2 = text.lastIndexOf('\r');
        if (index2 < 0) index2 = text.length();
        int index = Math.min(index1, index2);
        buffer.append(text.substring(0, index));
        if (index < text.length()) {
          buffer.append(" ...");
        }
      }
    }
    return buffer.toString();
  }

  public static String formatMethod(PsiMethod method, PsiSubstitutor substitutor, int options, int parameterOptions){
    return formatMethod(method, substitutor, options, parameterOptions, MAX_PARAMS_TO_SHOW);
  }

  public static String formatMethod(PsiMethod method, PsiSubstitutor substitutor, int options, int parameterOptions, int paramsToShow){
    StringBuffer buffer = new StringBuffer();
    if ((options & SHOW_MODIFIERS) != 0 && (options & MODIFIERS_AFTER) == 0){
      buffer.append(formatModifiers(method, options));
    }
    if ((options & SHOW_TYPE) != 0 && (options & TYPE_AFTER) == 0){
      PsiType type = method.getReturnType();
      if (type != null){
        if (buffer.length() > 0){
          buffer.append(' ');
        }
        buffer.append(formatType(type, options, substitutor));
      }
    }
    if ((options & SHOW_CONTAINING_CLASS) != 0){
      PsiClass aClass = method.getContainingClass();
      if (aClass != null){
        if (buffer.length() > 0){
          buffer.append(' ');
        }
        String name = aClass.getName();
        if (name != null) {
          if ((options & SHOW_FQ_NAME) != 0){
            String qName = aClass.getQualifiedName();
            if (qName != null){
              buffer.append(qName);
            }
            else{
              buffer.append(name);
            }
          }
          else{
            buffer.append(name);
          }
          buffer.append('.');
        }
      }
      if ((options & SHOW_NAME) != 0){
        buffer.append(method.getName());
      }
    }
    else{
      if ((options & SHOW_NAME) != 0){
        if (buffer.length() > 0){
          buffer.append(' ');
        }
        buffer.append(method.getName());
      }
    }
    if ((options & SHOW_PARAMETERS) != 0){
      buffer.append('(');
      PsiParameter[] parms = method.getParameterList().getParameters();
      for(int i = 0; i < Math.min(parms.length, paramsToShow); i++) {
        PsiParameter parm = parms[i];
        if (i > 0){
          buffer.append(", ");
        }
        buffer.append(formatVariable(parm, parameterOptions, substitutor));
      }
      if(parms.length > paramsToShow) {
        buffer.append (", ...");
      }
      buffer.append(')');
    }
    if ((options & SHOW_TYPE) != 0 && (options & TYPE_AFTER) != 0){
      PsiType type = method.getReturnType();
      if (type != null){
        if (buffer.length() > 0){
          buffer.append(':');
        }
        buffer.append(formatType(type, options, substitutor));
      }
    }
    if ((options & SHOW_MODIFIERS) != 0 && (options & MODIFIERS_AFTER) != 0){
      String modifiers = formatModifiers(method, options);
      if (modifiers.length() > 0){
        if (buffer.length() > 0){
          buffer.append(' ');
        }
        buffer.append(modifiers);
      }
    }
    if ((options & SHOW_THROWS) != 0){
      String throwsText = formatReferenceList(method.getThrowsList(), options);
      if (throwsText.length() > 0){
        if (buffer.length() > 0){
          buffer.append(' ');
        }
        //noinspection HardCodedStringLiteral
        buffer.append("throws ");
        buffer.append(throwsText);
      }
    }
    return buffer.toString();
  }

  public static String formatClass(PsiClass aClass, int options){
    StringBuffer buffer = new StringBuffer();
    if ((options & SHOW_MODIFIERS) != 0 && (options & MODIFIERS_AFTER) == 0){
      buffer.append(formatModifiers(aClass, options));
    }
    if ((options & SHOW_NAME) != 0){
      if (aClass instanceof PsiAnonymousClass && (options & SHOW_ANONYMOUS_CLASS_VERBOSE) != 0) {
        final PsiClassType baseClassReference = ((PsiAnonymousClass) aClass).getBaseClassType();
        PsiClass baseClass = baseClassReference.resolve();
        String name = baseClass == null ? baseClassReference.getPresentableText() : formatClass(baseClass, options);
        buffer.append(PsiBundle.message("anonymous.class.derived.display", name));
      }
      else {
        String name = aClass.getName();
        if (name != null) {
          if (buffer.length() > 0) {
            buffer.append(' ');
          }
          if ((options & SHOW_FQ_NAME) != 0) {
            String qName = aClass.getQualifiedName();
            if (qName != null) {
              buffer.append(qName);
            }
            else {
              buffer.append(aClass.getName());
            }
          }
          else {
            buffer.append(aClass.getName());
          }
        }
      }
    }
    if ((options & SHOW_MODIFIERS) != 0 && (options & MODIFIERS_AFTER) != 0){
      String modifiers = formatModifiers(aClass, options);
      if (modifiers.length() > 0){
        if (buffer.length() > 0){
          buffer.append(' ');
        }
        buffer.append(modifiers);
      }
    }
    if ((options & SHOW_EXTENDS_IMPLEMENTS) != 0){
      String extendsText = formatReferenceList(aClass.getExtendsList(), options);
      if (extendsText.length() > 0){
        if (buffer.length() > 0){
          buffer.append(' ');
        }
        //noinspection HardCodedStringLiteral
        buffer.append("extends ");
        buffer.append(extendsText);
      }
      String implementsText = formatReferenceList(aClass.getImplementsList(), options);
      if (implementsText.length() > 0){
        if (buffer.length() > 0){
          buffer.append(' ');
        }
        //noinspection HardCodedStringLiteral
        buffer.append("implements ");
        buffer.append(implementsText);
      }
    }
    return buffer.toString();
  }

  public static String formatModifiers(PsiElement element, int options) throws IllegalArgumentException{
    PsiModifierList list;
    if (element instanceof PsiVariable){
      list = ((PsiVariable)element).getModifierList();
    }
    else if (element instanceof PsiMethod){
      list = ((PsiMethod)element).getModifierList();
    }
    else if (element instanceof PsiClass){
      list = ((PsiClass)element).getModifierList();
      if (list == null) return "";
    }
    else if (element instanceof PsiClassInitializer){
      list = ((PsiClassInitializer)element).getModifierList();
      if (list == null) return "";
    }
    else{
      throw new IllegalArgumentException();
    }
    if (list == null) return "";
    PsiClass parentClass = element.getParent() instanceof PsiClass ? (PsiClass)element.getParent() : null;
    StringBuffer buffer = new StringBuffer();
    if (list.hasModifierProperty(PsiModifier.PUBLIC)){
      if ((options & SHOW_REDUNDANT_MODIFIERS) != 0 || parentClass == null || !parentClass.isInterface()){
        appendModifier(buffer, PsiModifier.PUBLIC);
      }
    }
    if (list.hasModifierProperty(PsiModifier.PROTECTED)){
      appendModifier(buffer, PsiModifier.PROTECTED);
    }
    if (list.hasModifierProperty(PsiModifier.PRIVATE)){
      appendModifier(buffer, PsiModifier.PRIVATE);
    }
    if (list.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) && (options & SHOW_PACKAGE_LOCAL) != 0){
      if (element instanceof PsiClass || element instanceof PsiMethod || element instanceof PsiField){
        if (element instanceof PsiClass && element.getParent() instanceof PsiDeclarationStatement){// local class
          appendModifier(buffer, PsiBundle.message("local.class.preposition"));
        }
        else{
          appendModifier(buffer, PsiBundle.message("package.local.visibility"));
        }
      }
    }
    if (list.hasModifierProperty(PsiModifier.STATIC)){
      if ((options & SHOW_REDUNDANT_MODIFIERS) != 0 ||
        !(element instanceof PsiField && parentClass != null && parentClass.isInterface())){
        appendModifier(buffer, PsiModifier.STATIC);
      }
    }
    if (list.hasModifierProperty(PsiModifier.ABSTRACT)){
      if ((options & SHOW_REDUNDANT_MODIFIERS) != 0 ||
        !(element instanceof PsiClass && ((PsiClass)element).isInterface()
          || element instanceof PsiMethod && parentClass != null && parentClass.isInterface())){
        appendModifier(buffer, PsiModifier.ABSTRACT);
      }
    }
    if (list.hasModifierProperty(PsiModifier.FINAL)){
      if ((options & SHOW_REDUNDANT_MODIFIERS) != 0 ||
        !(element instanceof PsiField && parentClass != null && parentClass.isInterface())){
        appendModifier(buffer, PsiModifier.FINAL);
      }
    }
    if (list.hasModifierProperty(PsiModifier.NATIVE) && (options & JAVADOC_MODIFIERS_ONLY) == 0){
      appendModifier(buffer, PsiModifier.NATIVE);
    }
    if (list.hasModifierProperty(PsiModifier.SYNCHRONIZED) && (options & JAVADOC_MODIFIERS_ONLY) == 0){
      appendModifier(buffer, PsiModifier.SYNCHRONIZED);
    }
    if (list.hasModifierProperty(PsiModifier.STRICTFP) && (options & JAVADOC_MODIFIERS_ONLY) == 0){
      appendModifier(buffer, PsiModifier.STRICTFP);
    }
    if (list.hasModifierProperty(PsiModifier.TRANSIENT) &&
        element instanceof PsiVariable // javac 5 puts transient attr for methods
       ){
      appendModifier(buffer, PsiModifier.TRANSIENT);
    }
    if (list.hasModifierProperty(PsiModifier.VOLATILE)){
      appendModifier(buffer, PsiModifier.VOLATILE);
    }
    if (buffer.length() > 0){
      buffer.setLength(buffer.length() - 1);
    }
    return buffer.toString();
  }

  private static void appendModifier(final StringBuffer buffer, final String modifier) {
    buffer.append(modifier);
    buffer.append(' ');
  }

  public static String formatReferenceList(PsiReferenceList list, int options){
    StringBuffer buffer = new StringBuffer();
    PsiJavaCodeReferenceElement[] refs = list.getReferenceElements();
    for(int i = 0; i < refs.length; i++) {
      PsiJavaCodeReferenceElement ref = refs[i];
      if (i > 0){
        buffer.append(", ");
      }
      buffer.append(formatReference(ref, options));
    }
    return buffer.toString();
  }

  public static String formatType(PsiType type, int options, PsiSubstitutor substitutor){
    type = substitutor.substitute(type);
    if ((options & SHOW_FQ_CLASS_NAMES) != 0){
      return type.getCanonicalText();
    }
    else{
      return type.getPresentableText();
    }
  }

  public static String formatReference(PsiJavaCodeReferenceElement ref, int options){
    if ((options & SHOW_FQ_CLASS_NAMES) != 0){
      return ref.getCanonicalText();
    }
    else{
      return ref.getText();
    }
  }
}
