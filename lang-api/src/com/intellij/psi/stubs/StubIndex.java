/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.Collection;

public abstract class StubIndex {
  public static StubIndex getInstance() {
    return ApplicationManager.getApplication().getComponent(StubIndex.class);
  }

  public abstract <Key, Psi extends PsiElement> Collection<Psi> get(StubIndexKey<Key, Psi> indexKey, Key key, final Project project,
                                                                    final GlobalSearchScope scope);
  public abstract <Key> Collection<Key> getAllKeys(StubIndexKey<Key, ?> indexKey);
}