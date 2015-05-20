/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ReferenceSetBase;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class PackageReferenceSet extends ReferenceSetBase<PsiPackageReference> {
  private final GlobalSearchScope mySearchScope;

  public PackageReferenceSet(@NotNull final String str, @NotNull final PsiElement element, final int startInElement) {
    this(str, element, startInElement, element.getResolveScope());
  }

  public PackageReferenceSet(@NotNull final String str, @NotNull final PsiElement element, final int startInElement, @NotNull GlobalSearchScope scope) {
    super(str, element, startInElement, DOT_SEPARATOR);
    mySearchScope=scope;
  }

  @Override
  @NotNull
  protected PsiPackageReference createReference(final TextRange range, final int index) {
    return new PsiPackageReference(this, range, index);
  }

  public Collection<PsiPackage> resolvePackageName(@Nullable PsiPackage context, final String packageName) {
    if (context != null) {
      return ContainerUtil.filter(context.getSubPackages(getResolveScope()), new Condition<PsiPackage>() {
        @Override
        public boolean value(PsiPackage aPackage) {
          return Comparing.equal(aPackage.getName(), packageName);
        }
      });
    }
    return Collections.emptyList();
  }

  @NotNull
  protected GlobalSearchScope getResolveScope() {
    return mySearchScope;
  }

  public Collection<PsiPackage> resolvePackage() {
    final PsiPackageReference packageReference = getLastReference();
    if (packageReference == null) {
      return Collections.emptyList();
    }
    return ContainerUtil.map2List(packageReference.multiResolve(false), new NullableFunction<ResolveResult, PsiPackage>() {
      @Override
      public PsiPackage fun(final ResolveResult resolveResult) {
        return (PsiPackage)resolveResult.getElement();
      }
    });
  }

  public Set<PsiPackage> getInitialContext() {
    return Collections.singleton(JavaPsiFacade.getInstance(getElement().getProject()).findPackage(""));
  }
}