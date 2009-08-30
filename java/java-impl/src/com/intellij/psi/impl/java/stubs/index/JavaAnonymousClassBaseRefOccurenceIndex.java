/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;

import java.util.Collection;

public class JavaAnonymousClassBaseRefOccurenceIndex extends StringStubIndexExtension<PsiAnonymousClass> {
  public static final StubIndexKey<String,PsiAnonymousClass> KEY = StubIndexKey.createIndexKey("java.anonymous.baseref");

  private static final JavaAnonymousClassBaseRefOccurenceIndex ourInstance = new JavaAnonymousClassBaseRefOccurenceIndex();
  public static JavaAnonymousClassBaseRefOccurenceIndex getInstance() {
    return ourInstance;
  }


  public StubIndexKey<String, PsiAnonymousClass> getKey() {
    return KEY;
  }

  public Collection<PsiAnonymousClass> get(final String s, final Project project, final GlobalSearchScope scope) {
    return super.get(s, project, new JavaSourceFilterScope(scope, project));
  }
}