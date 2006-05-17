/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlTag;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
public class ClassChooserManager {
  private static final Map<Class, ClassChooser> ourClassChoosers = new HashMap<Class, ClassChooser>();

  public static ClassChooser getClassChooser(final Class aClass) {
    final ClassChooser classChooser = ourClassChoosers.get(aClass);
    return classChooser != null ? classChooser : new ClassChooser() {
      public Class chooseClass(final XmlTag tag) {
        return aClass;
      }

      public void distinguishTag(final XmlTag tag, final Class aClass) {
      }

      public Class[] getChooserClasses() {
        return new Class[]{aClass};
      }
    };
  }

  public static <T extends DomElement> void registerClassChooser(final Class<T> aClass, final ClassChooser<T> classChooser, Disposable parentDisposable) {
    registerClassChooser(aClass, classChooser);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        unregisterClassChooser(aClass);
      }
    });
  }

  public static <T extends DomElement> void registerClassChooser(final Class<T> aClass, final ClassChooser<T> classChooser) {
    ourClassChoosers.put(aClass, classChooser);
  }

  public static <T extends DomElement> void unregisterClassChooser(Class<T> aClass) {
    ourClassChoosers.remove(aClass);
  }
}
