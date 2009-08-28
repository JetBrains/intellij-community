/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.util.ReflectionCache;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class ClassExtension<T> extends KeyedExtensionCollector<T, Class> {
  public ClassExtension(@NonNls final String epName) {
    super(epName);
  }

  protected String keyToString(final Class key) {
    return key.getName();
  }

  protected List<T> buildExtensions(final String key, final Class classKey) {
    final Set<String> allSupers = new THashSet<String>();
    collectSupers(classKey, allSupers);
    return buildExtensions(allSupers);
  }

  private static void collectSupers(Class classKey, Set<String> allSupers) {
    allSupers.add(classKey.getName());
    final Class[] interfaces = ReflectionCache.getInterfaces(classKey);
    for (final Class anInterface : interfaces) {
      collectSupers(anInterface, allSupers);
    }

    final Class superClass = ReflectionCache.getSuperClass(classKey);
    if (superClass != null) {
      collectSupers(superClass, allSupers);
    }
  }

  @Nullable
  public T forClass(Class t) {
    final List<T> ts = forKey(t);
    return ts.isEmpty() ? null : ts.get(0);
  }
}