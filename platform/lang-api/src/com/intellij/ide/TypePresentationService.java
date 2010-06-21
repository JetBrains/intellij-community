package com.intellij.ide;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author peter
 */
public class TypePresentationService {
  private final Map<String, NullableLazyValue<Icon>> myIcons = new HashMap<String, NullableLazyValue<Icon>>();
  private final Map<String, NullableLazyValue<String>> myNames = new HashMap<String, NullableLazyValue<String>>();
  private final FactoryMap<Class, Set<String>> mySuperClasses = new ConcurrentFactoryMap<Class, Set<String>>() {
    @Override
    protected Set<String> create(Class key) {
      final LinkedHashSet<String> result = new LinkedHashSet<String>();
      walkSupers(key, result);

      Set<String> knownSupers = new HashSet<String>();
      knownSupers.addAll(myIcons.keySet());
      knownSupers.addAll(myNames.keySet());
      result.retainAll(knownSupers);

      return result;
    }

    private void walkSupers(Class aClass, Set<String> result) {
      if (!result.add(aClass.getName())) {
        return;
      }
      final Class superClass = aClass.getSuperclass();
      if (superClass != null) walkSupers(superClass, result);

      for (Class intf : aClass.getInterfaces()) {
        walkSupers(intf, result);
      }
    }
  };

  public TypePresentationService() {
    for(TypeIconEP ep: Extensions.getExtensions(TypeIconEP.EP_NAME)) {
      myIcons.put(ep.className, ep.getIcon());
    }
    for(TypeNameEP ep: Extensions.getExtensions(TypeNameEP.EP_NAME)) {
      myNames.put(ep.className, ep.getTypeName());
    }
  }

  private static final TypePresentationService ourInstance = new TypePresentationService();
  public static TypePresentationService getService() {
    return ourInstance;
  }

  @Nullable
  public Icon getTypeIcon(Class type) {
    return getTypeInfoFromMap(type, myIcons);
  }

  @Nullable
  public String getTypePresentableName(Class type) {
    return getTypeInfoFromMap(type, myNames);
  }

  @Nullable
  private <T> T getTypeInfoFromMap(Class type, Map<String, NullableLazyValue<T>> map) {
    for (String qname : mySuperClasses.get(type)) {
      final NullableLazyValue<T> value = map.get(qname);
      if (value != null) {
        final T icon = value.getValue();
        if (icon != null) {
          return icon;
        }
      }
    }

    return null;
  }

}
