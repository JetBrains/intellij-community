/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class StubIndex {
  private static StubIndex ourInstance = CachedSingletonsRegistry.markCachedField(StubIndex.class);

  public static StubIndex getInstance() {
    if (ourInstance == null) {
      ourInstance = ApplicationManager.getApplication().getComponent(StubIndex.class);
    }
    return ourInstance;
  }

  public abstract <Key, Psi extends PsiElement> Collection<Psi> get(
      @NotNull StubIndexKey<Key, Psi> indexKey, @NotNull Key key, final Project project, final GlobalSearchScope scope
  );

  public abstract <Key> Collection<Key> getAllKeys(StubIndexKey<Key, ?> indexKey, @NotNull Project project);
}
