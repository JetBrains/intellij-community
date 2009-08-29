package com.intellij.execution;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public class PsiClassLocationUtil {
  private PsiClassLocationUtil() {
  }

  @Nullable
  public static Location<PsiClass> fromClassQualifiedName(final Project project, final String qualifiedName) {
    final PsiClass psiClass =
      JavaPsiFacade.getInstance(project).findClass(qualifiedName.replace('$', '.'), GlobalSearchScope.allScope(project));
    return psiClass != null ? new PsiLocation<PsiClass>(project, psiClass) : null;
  }
}
