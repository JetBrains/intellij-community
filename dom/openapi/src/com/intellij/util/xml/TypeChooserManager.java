/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.xml.XmlTag;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
public class TypeChooserManager {
  private final Map<Type, TypeChooser> myClassChoosers = new HashMap<Type, TypeChooser>();

  public TypeChooser getTypeChooser(final Type type) {
    final TypeChooser typeChooser = myClassChoosers.get(type);
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

  public void registerTypeChooser(final Type aClass, final TypeChooser typeChooser, Disposable parentDisposable) {
    registerTypeChooser(aClass, typeChooser);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        unregisterTypeChooser(aClass);
      }
    });
  }

  public void registerTypeChooser(final Type aClass, final TypeChooser typeChooser) {
    myClassChoosers.put(aClass, typeChooser);
  }

  public void unregisterTypeChooser(Type aClass) {
    myClassChoosers.remove(aClass);
  }

  public final void copyFrom(TypeChooserManager manager) {
    myClassChoosers.putAll(manager.myClassChoosers);
  }
}
