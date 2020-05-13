/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.util.javadoc;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.util.IncorrectOperationException;

/**
 *  @author dsl
 */
public class MethodJavaDocHelper {
  private static final Logger LOG = Logger.getInstance(MethodJavaDocHelper.class);
  private final PsiMethod myMethod;
  private final boolean myDoCorrectJavaDoc;
  private final PsiDocComment myDocComment;

  public MethodJavaDocHelper(PsiMethod method) {
    myMethod = method;
    myDocComment = myMethod.getDocComment();
    if (myDocComment == null) {
      myDoCorrectJavaDoc = false;
      return;
    }
    final PsiParameter[] parameters = myMethod.getParameterList().getParameters();
    if (parameters.length != 0) {
      final PsiDocTag[] paramTags = myDocComment.findTagsByName("param");
      if (paramTags.length > 0) {
        myDoCorrectJavaDoc = true;
      } else {
        myDoCorrectJavaDoc = false;
      }
    } else {
      myDoCorrectJavaDoc = true;
    }
  }

  public PsiDocTag getTagForParameter(PsiParameter parameter) {
    if (!myDoCorrectJavaDoc) return null;
    if (parameter == null) return null;
    final String name = parameter.getName();
    final PsiDocTag[] paramTags = myDocComment.findTagsByName("param");
    for (final PsiDocTag paramTag : paramTags) {
      final PsiElement[] dataElements = paramTag.getDataElements();
      if (dataElements.length > 0 && dataElements[0].getText().equals(name)) {
        return paramTag;
      }
    }
    return null;
  }

  public PsiDocTag addParameterAfter(String name, PsiDocTag anchor) throws IncorrectOperationException {
    if (!myDoCorrectJavaDoc) return null;
    if (anchor == null) return prependParameter(name);
    LOG.assertTrue(anchor.getParent() == myDocComment);
    final PsiDocTag paramTag = JavaPsiFacade.getElementFactory(myMethod.getProject()).createParamTag(name, "");
    return (PsiDocTag)myDocComment.addAfter(paramTag, anchor);
  }

  public PsiDocTag prependParameter(String name) throws IncorrectOperationException {
    if (!myDoCorrectJavaDoc) return null;
    final PsiDocTag[] paramTags = myDocComment.findTagsByName("param");
    final PsiDocTag newTag = JavaPsiFacade.getElementFactory(myMethod.getProject()).createParamTag(name, "");
    if (paramTags.length > 0) {
      return (PsiDocTag)myDocComment.addBefore(newTag, paramTags[0]);
    } else {
      return (PsiDocTag)myDocComment.add(newTag);
    }
  }

  public PsiDocTag appendParameter(String name) throws IncorrectOperationException {
    if (!myDoCorrectJavaDoc) return null;
    final PsiDocTag[] paramTags = myDocComment.findTagsByName("param");
    final PsiDocTag newTag = JavaPsiFacade.getElementFactory(myMethod.getProject()).createParamTag(name, "");
    if (paramTags.length > 0) {
      return (PsiDocTag)myDocComment.addAfter(newTag, paramTags[paramTags.length - 1]);
    } else {
      return (PsiDocTag)myDocComment.add(newTag);
    }
  }
}
