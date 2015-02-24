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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.util.PatternUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class PatternPackageReferenceSet extends PackageReferenceSet {

  public PatternPackageReferenceSet(String packageName, PsiElement element, int startInElement) {
    super(packageName, element, startInElement);
  }

  @Override
  public Collection<PsiPackage> resolvePackageName(@Nullable final PsiPackage context, final String packageName) {
    if (context == null) return Collections.emptySet();

    if (packageName.contains("*")) {
      final Pattern pattern = PatternUtil.fromMask(packageName);
      final Set<PsiPackage> packages = new LinkedHashSet<PsiPackage>();

      processSubPackages(context, new Processor<PsiPackage>() {
        @Override
        public boolean process(PsiPackage psiPackage) {
          String name = psiPackage.getName();
          if (name != null && pattern.matcher(name).matches()) {
            packages.add(psiPackage);
          }
          return true;
        }
      });

      return packages;
    }

    return super.resolvePackageName(context, packageName);
  }

  protected static boolean processSubPackages(final PsiPackage pkg, final Processor<PsiPackage> processor) {
    if (!processor.process(pkg)) return false;

    for (final PsiPackage aPackage : pkg.getSubPackages()) {
      if (!processSubPackages(aPackage, processor)) return false;
    }
    return true;
  }
}
