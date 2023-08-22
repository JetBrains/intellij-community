// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.org.objectweb.asm.tree.FieldInsnNode;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class HardCodedPurity {
  static final boolean AGGRESSIVE_HARDCODED_PURITY = Registry.is("java.annotations.inference.aggressive.hardcoded.purity", true);

  private static final Set<Member> thisChangingMethods = Set.of(
    new Member("java/lang/Throwable", "fillInStackTrace", "()Ljava/lang/Throwable;")
  );
  // Assumed that all these methods are not only pure, but return object which could be safely modified
  private static final Set<Member> pureMethods = Set.of(
    // Maybe overloaded and be not pure, but this would be definitely bad code style
    // Used in Throwable(Throwable) ctor, so this helps to infer purity of many exception constructors
    new Member("java/lang/Throwable", "toString", "()Ljava/lang/String;"),
    // Cycle in AbstractStringBuilder ctor and this method disallows to infer the purity
    new Member("java/lang/StringUTF16", "newBytesFor", "(I)[B"),
    // Declared in final class StringBuilder
    new Member("java/lang/StringBuilder", "toString", "()Ljava/lang/String;"),
    new Member("java/lang/StringBuffer", "toString", "()Ljava/lang/String;"),
    // Often used in generated code since Java 9; to avoid too many equations
    new Member("java/util/Objects", "requireNonNull", "(Ljava/lang/Object;)Ljava/lang/Object;"),
    // Caches hashCode, but it's better to suppose it's pure
    new Member("java/lang/String", "hashCode", "()I"),
    // Native
    new Member("java/lang/Object", "getClass", "()Ljava/lang/Class;"),
    new Member("java/lang/Class", "getComponentType", "()Ljava/lang/Class;"),
    new Member("java/lang/reflect/Array", "newInstance", "(Ljava/lang/Class;I)Ljava/lang/Object;"),
    new Member("java/lang/reflect/Array", "newInstance", "(Ljava/lang/Class;[I)Ljava/lang/Object;"),
    new Member("java/lang/Float", "floatToRawIntBits", "(F)I"),
    new Member("java/lang/Float", "intBitsToFloat", "(I)F"),
    new Member("java/lang/Double", "doubleToRawLongBits", "(D)J"),
    new Member("java/lang/Double", "longBitsToDouble", "(J)D")
  );
  private static final Map<Member, Set<EffectQuantum>> solutions = new HashMap<>();
  private static final Set<EffectQuantum> thisChange = Set.of(EffectQuantum.ThisChangeQuantum);

  static {
    // Native
    solutions.put(new Member("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V"),
                  Set.of(new EffectQuantum.ParamChangeQuantum(2)));
    solutions.put(new Member("java/lang/Object", "hashCode", "()I"), Set.of());
  }

  static HardCodedPurity getInstance() {
    return Holder.INSTANCE;
  }

  Effects getHardCodedSolution(Member method) {
    if (isThisChangingMethod(method)) {
      return new Effects(isBuilderChainCall(method) ? DataValue.ThisDataValue : DataValue.UnknownDataValue1, thisChange);
    }
    else if (isPureMethod(method)) {
      return new Effects(getReturnValueForPureMethod(method), Collections.emptySet());
    }
    else {
      Set<EffectQuantum> effects = solutions.get(method);
      return effects == null ? null : new Effects(DataValue.UnknownDataValue1, effects);
    }
  }

  boolean isThisChangingMethod(Member method) {
    return isBuilderChainCall(method) || thisChangingMethods.contains(method);
  }

  boolean isBuilderChainCall(Member method) {
    // Those methods are virtual, thus contracts cannot be inferred automatically,
    // but all possible implementations are controlled
    // (only final classes j.l.StringBuilder and j.l.StringBuffer extend package-private j.l.AbstractStringBuilder)
    return (method.internalClassName.equals("java/lang/StringBuilder") || method.internalClassName.equals("java/lang/StringBuffer")) &&
           method.methodName.startsWith("append");
  }

  DataValue getReturnValueForPureMethod(Member method) {
    String type = StringUtil.substringAfter(method.methodDesc, ")");
    if (type != null && (type.length() == 1 || type.equals("Ljava/lang/String;") || type.equals("Ljava/lang/Class;"))) {
      return DataValue.UnknownDataValue1;
    }
    return DataValue.LocalDataValue;
  }

  boolean isPureMethod(Member method) {
    if (pureMethods.contains(method)) {
      return true;
    }
    // Array clone() method is a special beast: it's qualifier class is array itself
    if (method.internalClassName.startsWith("[") && method.methodName.equals("clone") && method.methodDesc.equals("()Ljava/lang/Object;")) {
      return true;
    }
    return false;
  }

  boolean isOwnedField(FieldInsnNode fieldInsn) {
    return fieldInsn.owner.equals("java/lang/AbstractStringBuilder") && fieldInsn.name.equals("value");
  }

  static class AggressiveHardCodedPurity extends HardCodedPurity {
    static final Set<String> ITERABLES = Set.of("java/lang/Iterable", "java/util/Collection",
                                                           "java/util/List", "java/util/Set", "java/util/ArrayList",
                                                           "java/util/HashSet", "java/util/AbstractList",
                                                           "java/util/AbstractSet", "java/util/TreeSet");

    @Override
    boolean isThisChangingMethod(Member method) {
      if (method.methodName.equals("next") && method.methodDesc.startsWith("()") && method.internalClassName.equals("java/util/Iterator")) {
        return true;
      }
      if (method.methodName.equals("initCause") && method.methodDesc.equals("(Ljava/lang/Throwable;)Ljava/lang/Throwable;") &&
          method.internalClassName.startsWith("java/")) {
        // Throwable.initCause is overridable. For Java classes, we assume that its contract is fixed 
        return true;
      }
      return super.isThisChangingMethod(method);
    }

    @Override
    boolean isPureMethod(Member method) {
      if (method.methodName.equals("toString") && method.methodDesc.equals("()Ljava/lang/String;")) return true;
      if (method.methodName.equals("iterator") && method.methodDesc.equals("()Ljava/util/Iterator;") &&
          ITERABLES.contains(method.internalClassName)) {
        return true;
      }
      if (method.methodName.equals("hasNext") && method.methodDesc.equals("()Z") && method.internalClassName.equals("java/util/Iterator")) {
        return true;
      }
      return super.isPureMethod(method);
    }
  }

  private static final class Holder {
    static final HardCodedPurity INSTANCE = AGGRESSIVE_HARDCODED_PURITY ? new AggressiveHardCodedPurity() : new HardCodedPurity();
  }
}