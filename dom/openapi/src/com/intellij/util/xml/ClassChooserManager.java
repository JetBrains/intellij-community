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
public class ClassChooserManager {
  private static final Map<Type, ClassChooser> ourClassChoosers = new HashMap<Type, ClassChooser>();

  public static ClassChooser getClassChooser(final Type type) {
    final ClassChooser classChooser = ourClassChoosers.get(type);
    return classChooser != null ? classChooser : new ClassChooser() {
      public Type chooseType(final XmlTag tag) {
        return type;
      }

      public void distinguishTag(final XmlTag tag, final Type aClass) {
      }

      public Type[] getChooserClasses() {
        return new Type[]{type};
      }
    };
  }

  public static void registerClassChooser(final Type aClass, final ClassChooser classChooser, Disposable parentDisposable) {
    registerClassChooser(aClass, classChooser);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        unregisterClassChooser(aClass);
      }
    });
  }

  public static void registerClassChooser(final Type aClass, final ClassChooser classChooser) {
    ourClassChoosers.put(aClass, classChooser);
  }

  public static void unregisterClassChooser(Type aClass) {
    ourClassChoosers.remove(aClass);
  }
}
