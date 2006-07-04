/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlTag;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;

import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Type;

/**
 * @author peter
 */
public class TypeChooserManager {
  private static final Map<Type, TypeChooser> ourClassChoosers = new HashMap<Type, TypeChooser>();

  public static TypeChooser getClassChooser(final Type type) {
    final TypeChooser typeChooser = ourClassChoosers.get(type);
    return typeChooser != null ? typeChooser : new TypeChooser() {
      public Type chooseType(final XmlTag tag) {
        return type;
      }

      public void distinguishTag(final XmlTag tag, final Type aClass) {
      }

      public Type[] getChooserTypes() {
        return new Type[]{type};
      }
    };
  }

  public static void registerClassChooser(final Type aClass, final TypeChooser typeChooser, Disposable parentDisposable) {
    registerClassChooser(aClass, typeChooser);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        unregisterClassChooser(aClass);
      }
    });
  }

  public static void registerClassChooser(final Type aClass, final TypeChooser typeChooser) {
    ourClassChoosers.put(aClass, typeChooser);
  }

  public static void unregisterClassChooser(Type aClass) {
    ourClassChoosers.remove(aClass);
  }
}
