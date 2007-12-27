package com.intellij.util.xml;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.Function;
import net.sf.cglib.proxy.Factory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TypeNameManager {
  public static final Map<Class, String> ourTypeNames = new HashMap<Class, String>();
  public static final List<Function<Class, String>> ourTypeProviders = new ArrayList<Function<Class, String>>();

  private TypeNameManager() {
  }

  public static void registerTypeProvider(Function<Class, String> function) { ourTypeProviders.add(function); }

  public static void unregisterTypeProvider(Function<Class, String> function) { ourTypeProviders.remove(function); }

  public static String getTypeName(Class aClass) {
    String s = _getTypeName(aClass);
    if (s != null) return s;
    return getDefaultTypeName(aClass);
  }

  public static String getDefaultTypeName(final Class aClass) {
    String simpleName = aClass.getSimpleName();
    final int i = simpleName.indexOf('$');
    if (i >= 0) {
      if (Factory.class.isAssignableFrom(aClass)) {
        simpleName = simpleName.substring(0, i);
      } else {
        simpleName = simpleName.substring(i + 1);
      }
    }
    return StringUtil.capitalizeWords(StringUtil.join(NameUtil.nameToWords(simpleName),  " "), true);
  }

  @Nullable
  public static <T> T getFromClassMap(Map<Class,T> map, Class value) {
    for (final Map.Entry<Class, T> entry : map.entrySet()) {
      if (entry.getKey().isAssignableFrom(value)) {
        return entry.getValue();
      }
    }
    return null;
  }

  @Nullable
  public static String _getTypeName(final Class aClass) {
    for (final Function<Class, String> function : ourTypeProviders) {
      final String s = function.fun(aClass);
      if (s != null) {
        return s;
      }
    }
    return getFromClassMap(ourTypeNames, aClass);
  }

  public static void registerTypeName(Class aClass, String typeName) { ourTypeNames.put(aClass, typeName); }
}
