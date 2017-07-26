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
package com.intellij.compiler.backwardRefs;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.backwardRefs.LightRef;
import org.jetbrains.backwardRefs.NameEnumerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class JavaLightUsageAdapter implements LanguageLightRefAdapter {
  @NotNull
  @Override
  public Set<FileType> getFileTypes() {
    return ContainerUtil.set(JavaFileType.INSTANCE, JavaClassFileType.INSTANCE);
  }

  @Override
  public LightRef asLightUsage(@NotNull PsiElement element, @NotNull NameEnumerator names) throws IOException {
    if (mayBeVisibleOutsideOwnerFile(element)) {
      if (element instanceof PsiField) {
        final PsiField field = (PsiField)element;
        final PsiClass aClass = field.getContainingClass();
        if (aClass == null || aClass instanceof PsiAnonymousClass) return null;
        final String jvmOwnerName = ClassUtil.getJVMClassName(aClass);
        final String name = field.getName();
        if (name == null || jvmOwnerName == null) return null;
        final int ownerId = names.tryEnumerate(jvmOwnerName);
        if (ownerId == 0) return null;
        final int nameId = names.tryEnumerate(name);
        if (nameId == 0) return null;
        return new LightRef.JavaLightFieldRef(ownerId, nameId);
      }
      else if (element instanceof PsiMethod) {
        final PsiClass aClass = ((PsiMethod)element).getContainingClass();
        if (aClass == null || aClass instanceof PsiAnonymousClass) return null;
        final String jvmOwnerName = ClassUtil.getJVMClassName(aClass);
        if (jvmOwnerName == null) return null;
        final PsiMethod method = (PsiMethod)element;
        final String name = method.isConstructor() ? "<init>" : method.getName();
        final int parametersCount = method.getParameterList().getParametersCount();
        final int ownerId = names.tryEnumerate(jvmOwnerName);
        if (ownerId == 0) return null;
        final int nameId = names.tryEnumerate(name);
        if (nameId == 0) return null;
        return new LightRef.JavaLightMethodRef(ownerId, nameId, parametersCount);
      }
      else if (element instanceof PsiClass) {
        final String jvmClassName = ClassUtil.getJVMClassName((PsiClass)element);
        if (jvmClassName != null) {
          final int nameId = names.tryEnumerate(jvmClassName);
          if (nameId != 0) {
            return new LightRef.JavaLightClassRef(nameId);
          }
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public List<LightRef> getHierarchyRestrictedToLibraryScope(@NotNull LightRef baseRef,
                                                             @NotNull PsiElement basePsi,
                                                             @NotNull NameEnumerator names, @NotNull GlobalSearchScope libraryScope)
    throws IOException {
    final PsiClass baseClass = ObjectUtils.notNull(basePsi instanceof PsiClass ? (PsiClass)basePsi : ReadAction.compute(() -> (PsiMember)basePsi).getContainingClass());

    final List<LightRef> overridden = new ArrayList<>();
    final IOException[] exception = new IOException[]{null};
    Processor<PsiClass> processor = c -> {
      if (c.hasModifierProperty(PsiModifier.PRIVATE)) return true;
      String qName = ReadAction.compute(() -> c.getQualifiedName());
      if (qName == null) return true;
      try {
        final int nameId = names.tryEnumerate(qName);
        if (nameId != 0) {
          overridden.add(baseRef.override(nameId));
        }
      }
      catch (IOException e) {
        exception[0] = e;
        return false;
      }
      return true;
    };
    if (exception[0] != null) {
      throw exception[0];
    }

    ClassInheritorsSearch.search(baseClass, LibraryScopeCache.getInstance(baseClass.getProject()).getLibrariesOnlyScope(), true).forEach(processor);
    return overridden;

  }

  @NotNull
  @Override
  public Class<? extends LightRef.LightClassHierarchyElementDef> getHierarchyObjectClass() {
    return LightRef.LightClassHierarchyElementDef.class;
  }

  @NotNull
  @Override
  public Class<? extends LightRef> getFunExprClass() {
    return LightRef.JavaLightFunExprDef.class;
  }

  @NotNull
  @Override
  public PsiClass[] findDirectInheritorCandidatesInFile(@NotNull SearchId[] internalNames,
                                                        @NotNull PsiFileWithStubSupport file) {
    return JavaCompilerElementRetriever.retrieveClassesByInternalIds(internalNames, file);
  }

  @NotNull
  @Override
  public PsiFunctionalExpression[] findFunExpressionsInFile(@NotNull SearchId[] funExpressions,
                                                            @NotNull PsiFileWithStubSupport file) {
    TIntHashSet requiredIndices = new TIntHashSet(funExpressions.length);
    for (SearchId funExpr : funExpressions) {
      requiredIndices.add(funExpr.getId());
    }
    return JavaCompilerElementRetriever.retrieveFunExpressionsByIndices(requiredIndices, file);
  }

  @Override
  public boolean isClass(@NotNull PsiElement element) {
    return element instanceof PsiClass;
  }

  @NotNull
  @Override
  public PsiElement[] getInstantiableConstructors(@NotNull PsiElement aClass) {
    if (!(aClass instanceof PsiClass)) {
      throw new IllegalArgumentException("parameter should be an instance of PsiClass: " + aClass);
    }
    PsiClass theClass = (PsiClass)aClass;
    if (theClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return PsiElement.EMPTY_ARRAY;
    }

    return Stream.of(theClass.getConstructors()).filter(c -> !c.hasModifierProperty(PsiModifier.PRIVATE)).toArray(s -> PsiElement.ARRAY_FACTORY.create(s));
  }

  @Override
  public boolean isDirectInheritor(PsiElement candidate, PsiNamedElement baseClass) {
    return ((PsiClass) candidate).isInheritor((PsiClass) baseClass, false);
  }

  private static boolean mayBeVisibleOutsideOwnerFile(@NotNull PsiElement element) {
    if (!(element instanceof PsiModifierListOwner)) return true;
    if (((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.PRIVATE)) return false;
    return true;
  }
}
