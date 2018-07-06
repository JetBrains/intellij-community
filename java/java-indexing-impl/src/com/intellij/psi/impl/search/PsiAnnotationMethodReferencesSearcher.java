// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.jvm.JvmClass;
import com.intellij.lang.jvm.JvmClassKind;
import com.intellij.lang.jvm.JvmMethod;
import com.intellij.model.Symbol;
import com.intellij.model.SymbolReference;
import com.intellij.model.search.SearchRequestCollector;
import com.intellij.model.search.SearchRequestor;
import com.intellij.model.search.SymbolReferenceSearchParameters;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiReference;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME;

public class PsiAnnotationMethodReferencesSearcher implements SearchRequestor {

  @Override
  public void collectSearchRequests(@NotNull SearchRequestCollector collector) {
    SymbolReferenceSearchParameters parameters = collector.getParameters();

    Symbol refElement = parameters.getTarget();
    if (!(refElement instanceof JvmMethod)) return;

    JvmMethod method = (JvmMethod)refElement;
    JvmClass clazz = ReadAction.compute(() -> method.getContainingClass());
    if (clazz == null) return;

    boolean isAnnotationClass = ReadAction.compute(() -> clazz.getClassKind()) != JvmClassKind.ANNOTATION;
    if (isAnnotationClass) return;

    boolean isValueMethod = ReadAction.compute(() -> DEFAULT_REFERENCED_METHOD_NAME.equals(method.getName()) && !method.hasParameters());
    if (!isValueMethod) return;

    collector.searchTarget(clazz)
             .restrictSearchScopeTo(JavaFileType.INSTANCE)
             .search(PsiAnnotationMethodReferencesSearcher::createImplicitDefaultAnnotationMethodConsumer);
  }

  @NotNull
  static Processor<SymbolReference> createImplicitDefaultAnnotationMethodConsumer(@NotNull Processor<? super PsiReference> consumer) {
    return ReadActionProcessor.wrapInReadAction(reference -> {
      if (reference instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement javaReference = (PsiJavaCodeReferenceElement)reference;
        if (javaReference.getParent() instanceof PsiAnnotation) {
          PsiNameValuePair[] members = ((PsiAnnotation)javaReference.getParent()).getParameterList().getAttributes();
          if (members.length == 1 && members[0].getNameIdentifier() == null) {
            PsiReference t = members[0].getReference();
            if (t != null && !consumer.process(t)) return false;
          }
        }
      }
      return true;
    });
  }
}
