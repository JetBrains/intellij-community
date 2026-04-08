// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumentationClassFinder.PseudoClass;
import com.intellij.compiler.instrumentation.InstrumentationClassFinder.PseudoMethod;
import java.beans.Introspector;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Properties provider that uses ASM-based bytecode reading ({@link InstrumentationClassFinder}).
 * <p>
 * Compared to {@link CompiledClassPropertiesProvider} this avoids classloader issues when component classes reference types from non-base
 * JDK modules (e.g., {@code java.sql.Timestamp}) that are not accessible through the compilation classloader.
 *
 * @see CompiledClassPropertiesProvider
 */
public final class AsmClassPropertiesProvider implements PropertiesProvider {
  private final InstrumentationClassFinder myFinder;
  private final HashMap<String, HashMap<String, LwIntrospectedProperty>> myCache = new HashMap<>();

  public AsmClassPropertiesProvider(@NotNull InstrumentationClassFinder finder) {
    myFinder = finder;
  }

  @Override
  public HashMap<String, LwIntrospectedProperty> getLwProperties(String className) {
    if (myCache.containsKey(className)) {
      return myCache.get(className);
    }

    if ("com.intellij.uiDesigner.HSpacer".equals(className) || "com.intellij.uiDesigner.VSpacer".equals(className)) {
      return null;
    }

    HashMap<String, LwIntrospectedProperty> result = new HashMap<>();
    try {
      PseudoClass componentClass = myFinder.loadClass(className);
      PseudoClass jComponentClass = myFinder.loadClass("javax.swing.JComponent");
      if (!jComponentClass.isAssignableFrom(componentClass)) {
        myCache.put(className, null);
        return null;
      }

      LinkedHashMap<String, GetterInfo> getters = new LinkedHashMap<>();
      for (PseudoClass c = componentClass; c != null; c = c.getSuperClass()) {
        collectGetters(c, getters);
      }

      for (String propName : getters.keySet()) {
        GetterInfo getter = getters.get(propName);
        if (hasMatchingSetter(componentClass, propName, getter.typeDescriptor)) {
          LwIntrospectedProperty property = createProperty(propName, getter.typeName);
          if (property != null) {
            property.setDeclaringClassName(getter.declaringClassName);
            result.put(propName, property);
          }
        }
      }
    }
    catch (Exception e) {
      // Class not loadable or hierarchy traversal failed — return what we have (may be empty)
    }

    myCache.put(className, result);
    return result;
  }

  private static void collectGetters(PseudoClass cls, LinkedHashMap<String, GetterInfo> getters) {
    String declaringClassName = cls.getName().replace('/', '.');
    List<PseudoMethod> methods = cls.getMethods();
    for (PseudoMethod method : methods) {
      if ((method.getModifiers() & Opcodes.ACC_BRIDGE) != 0) {
        continue;
      }
      String name = method.getName();
      String descriptor = method.getSignature();

      Type returnType = Type.getReturnType(descriptor);
      Type[] argTypes = Type.getArgumentTypes(descriptor);

      // Getter: 0 params, non-void return
      if (argTypes.length != 0 || returnType.getSort() == Type.VOID) {
        continue;
      }

      String propName = null;
      if (name.length() > 3 && name.startsWith("get")) {
        propName = Introspector.decapitalize(name.substring(3));
      }
      else if (name.length() > 2 && name.startsWith("is") &&
               (returnType.getSort() == Type.BOOLEAN ||
                "java.lang.Boolean".equals(returnType.getClassName()))) {
        propName = Introspector.decapitalize(name.substring(2));
      }

      if (propName != null && !getters.containsKey(propName)) {
        getters.put(propName, new GetterInfo(returnType.getDescriptor(), returnType.getClassName(), declaringClassName));
      }
    }
  }

  private static boolean hasMatchingSetter(PseudoClass componentClass, String propName, String typeDescriptor) throws IOException, ClassNotFoundException {
    String setterName = "set" + Character.toUpperCase(propName.charAt(0)) + propName.substring(1);
    // For properties where decapitalize preserved the original case (e.g., URL), use the name as-is
    if (propName.length() > 1 && Character.isUpperCase(propName.charAt(0)) && Character.isUpperCase(propName.charAt(1))) {
      setterName = "set" + propName;
    }

    String expectedDescriptor = "(" + typeDescriptor + ")V";
    for (PseudoClass c = componentClass; c != null; c = c.getSuperClass()) {
      for (PseudoMethod method : c.getMethods()) {
        if ((method.getModifiers() & Opcodes.ACC_BRIDGE) != 0) {
          continue;
        }
        if (method.getName().equals(setterName) && method.getSignature().equals(expectedDescriptor)) {
          return true;
        }
      }
    }
    return false;
  }

  private LwIntrospectedProperty createProperty(String propName, String typeName) {
    // Try known types first (pure string matching, no class loading)
    LwIntrospectedProperty property = CompiledClassPropertiesProvider.propertyFromClassName(typeName, propName);
    if (property != null) {
      return property;
    }

    // For unknown types, try PseudoClass-based hierarchy checks
    try {
      PseudoClass propClass = myFinder.loadClass(typeName);

      PseudoClass componentBase = myFinder.loadClass("java.awt.Component");
      if (componentBase.isAssignableFrom(propClass)) {
        return new LwIntroComponentProperty(propName, typeName);
      }

      PseudoClass listModelBase = myFinder.loadClass("javax.swing.ListModel");
      if (listModelBase.isAssignableFrom(propClass)) {
        return new LwIntroListModelProperty(propName, typeName);
      }

      // Enum check
      PseudoClass superClass = propClass.getSuperClass();
      if (superClass != null && "java/lang/Enum".equals(superClass.getName())) {
        try {
          Class<?> enumClass = Class.forName(typeName, false, myFinder.getLoader());
          return new LwIntroEnumProperty(propName, enumClass);
        }
        catch (Exception ignored) {
          // Can't load enum class — skip this property
        }
      }
    }
    catch (Exception ignored) {
      // Property type not loadable — skip this property gracefully
    }
    return null;
  }

  private static final class GetterInfo {
    final String typeDescriptor;
    final String typeName;
    final String declaringClassName;

    GetterInfo(String typeDescriptor, String typeName, String declaringClassName) {
      this.typeDescriptor = typeDescriptor;
      this.typeName = typeName;
      this.declaringClassName = declaringClassName;
    }
  }
}
