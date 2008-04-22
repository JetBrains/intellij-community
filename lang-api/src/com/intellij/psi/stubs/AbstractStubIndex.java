/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.Collection;

public abstract class AbstractStubIndex<Key, Psi extends PsiElement> implements StubIndexExtension<Key, Psi> {
  public Collection<Key> getAllKeys() {
    return StubIndex.getInstance().getAllKeys(getKey());
  }

  public Collection<Psi> get(Key key, final Project project, final GlobalSearchScope scope) {
    return StubIndex.getInstance().get(getKey(), key, project, scope);
  }

}