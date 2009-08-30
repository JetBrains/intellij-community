/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;

import java.util.Collection;

public class JavaAnnotationIndex extends StringStubIndexExtension<PsiAnnotation> {
  public static final StubIndexKey<String,PsiAnnotation> KEY = StubIndexKey.createIndexKey("java.annotations");

  private static final JavaAnnotationIndex ourInstance = new JavaAnnotationIndex();
  public static JavaAnnotationIndex getInstance() {
    return ourInstance;
  }

  public StubIndexKey<String, PsiAnnotation> getKey() {
    return KEY;
  }

  public Collection<PsiAnnotation> get(final String s, final Project project, final GlobalSearchScope scope) {
    return super.get(s, project, new JavaSourceFilterScope(scope, project));
  }
}