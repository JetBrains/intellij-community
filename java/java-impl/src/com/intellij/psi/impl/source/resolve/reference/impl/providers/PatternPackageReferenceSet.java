package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PackageReferenceSet;
import com.intellij.util.PatternUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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
      final Set<PsiPackage> packages = new HashSet<PsiPackage>();

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
    else {
      return super.resolvePackageName(context, packageName);
    }
  }

   protected static boolean processSubPackages(final PsiPackage pkg, final Processor<PsiPackage> processor) {
    if (!processor.process(pkg)) return false;
    for (final PsiPackage aPackage : pkg.getSubPackages()) {
      if (!processSubPackages(aPackage, processor)) return false;
    }
    return true;
  }
}
