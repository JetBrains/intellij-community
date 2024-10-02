// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class CodeFragmentFactoryContextWrapper extends CodeFragmentFactory {
  public static final Key<Value> LABEL_VARIABLE_VALUE_KEY = Key.create("_label_variable_value_key_");
  public static final String DEBUG_LABEL_SUFFIX = "_DebugLabel";

  private final CodeFragmentFactory myDelegate;

  public CodeFragmentFactoryContextWrapper(CodeFragmentFactory delegate) {
    myDelegate = delegate;
  }

  @Override
  public PsiCodeFragment createPsiCodeFragment(TextWithImports item, PsiElement context, Project project) {
    return prepareResolveScope(myDelegate.createPsiCodeFragment(item, wrapContext(project, context), project));
  }

  @Override
  public PsiCodeFragment createPresentationPsiCodeFragment(TextWithImports item, PsiElement context, Project project) {
    return prepareResolveScope(myDelegate.createPresentationPsiCodeFragment(item, wrapContext(project, context), project));
  }

  @Override
  public boolean isContextAccepted(PsiElement contextElement) {
    return myDelegate.isContextAccepted(contextElement);
  }

  @Override
  @NotNull
  public LanguageFileType getFileType() {
    return myDelegate.getFileType();
  }

  @Override
  public EvaluatorBuilder getEvaluatorBuilder() {
    return myDelegate.getEvaluatorBuilder();
  }

  private static PsiCodeFragment prepareResolveScope(PsiCodeFragment codeFragment) {
    GlobalSearchScope originalResolveScope = codeFragment.getResolveScope();
    codeFragment.forceResolveScope(new DelegatingGlobalSearchScope(GlobalSearchScope.allScope(codeFragment.getProject())) {
      final Comparator<VirtualFile> myScopeComparator = Comparator.comparing(originalResolveScope::contains).thenComparing(super::compare);

      @Override
      public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
        // prefer files from the original resolve scope
        return myScopeComparator.compare(file1, file2);
      }
    });
    return codeFragment;
  }

  private PsiElement wrapContext(Project project, final PsiElement originalContext) {
    if (project.isDefault()) return originalContext;
    EvaluationContextWrapper wrapper = myDelegate.createEvaluationContextWrapper();
    if (wrapper == null) return originalContext;
    return wrapper.wrapContext(project, originalContext, AdditionalContextProvider.getAllAdditionalContextElements(project, originalContext));
  }
}
