/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.util.ReflectionCache;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ClassExtension<T> extends KeyedExtensionCollector<T, Class> {
  public ClassExtension(@NonNls final String epName) {
    super(epName);
  }

  protected String keyToString(final Class key) {
    return key.getName();
  }

  protected List<T> buildExtensions(final String key, final Class classKey) {
    final List<T> ts = super.buildExtensions(key, classKey);

    final Class[] interfaces = ReflectionCache.getInterfaces(classKey);
    for (final Class anInterface : interfaces) {
      ts.addAll(buildExtensions(anInterface.getName(), anInterface));
    }

    final Class superClass = ReflectionCache.getSuperClass(classKey);
    if (superClass != null) {
      ts.addAll(buildExtensions(superClass.getName(), superClass));
    }
    return ts;
  }

  @Nullable
  public T forClass(Class t) {
    final List<T> ts = forKey(t);
    return ts.isEmpty() ? null : ts.get(0);
  }
}