// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist;

import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.util.TypeConversionUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Predicate;

/**
 * An object that encapsulates information about JDI value necessary to fill the DfaMemoryState without requesting the debugged process.
 * The idea is to gather all the necessary info from JDI outside of read action, encapsulate it inside JdiValueInfo, then apply to
 * the memory state.
 */
interface JdiValueInfo {
  Set<String> COLLECTIONS_WITH_SIZE_FIELD = Set.of(
    CommonClassNames.JAVA_UTIL_ARRAY_LIST, CommonClassNames.JAVA_UTIL_LINKED_LIST, CommonClassNames.JAVA_UTIL_HASH_MAP, "java.util.TreeMap",
    "java.util.ImmutableCollections$SetN", "java.util.ImmutableCollections$MapN");

  class PrimitiveConstant implements JdiValueInfo {
    private final DfConstantType<?> myDfType;

    private PrimitiveConstant(DfConstantType<?> type) {
      myDfType = type;
    }

    public DfConstantType<?> getDfType() {
      return myDfType;
    }
  }

  class StringConstant implements JdiValueInfo {
    private final @NotNull String myValue;

    StringConstant(@NotNull String value) {
      myValue = value;
    }

    public @NotNull String getValue() {
      return myValue;
    }
  }

  class ObjectRef implements JdiValueInfo {
    private final @NotNull String mySignature;

    ObjectRef(@NotNull ReferenceType type) {
      mySignature = type.signature();
    }

    public @NotNull String getSignature() {
      return mySignature;
    }
  }

  class EnumConstant extends ObjectRef {
    private final @NotNull String myName;


    EnumConstant(@NotNull ReferenceType type, @NotNull String name) {
      super(type);
      myName = name;
    }

    public @NotNull String getName() {
      return myName;
    }
  }

  class ObjectWithSpecialField extends ObjectRef {
    private final @NotNull SpecialField myField;
    private final @NotNull JdiValueInfo myValue;

    ObjectWithSpecialField(@NotNull ReferenceType type, @NotNull SpecialField field, @NotNull JdiValueInfo value) {
      super(type);
      myField = field;
      myValue = value;
    }

    public @NotNull SpecialField getField() {
      return myField;
    }

    public @NotNull JdiValueInfo getValue() {
      return myValue;
    }
  }

  static @Nullable JdiValueInfo from(@NotNull Value value, @NotNull Predicate<? super ClassLoaderReference> classLoaderFilter) {
    return from(value, classLoaderFilter, true);
  }

  private static @Nullable JdiValueInfo from(@NotNull Value value,
                                             @NotNull Predicate<? super ClassLoaderReference> classLoaderFilter,
                                             boolean fillSpecial) {
    DfConstantType<?> constant = primitiveConstant(value);
    if (constant != null) {
      return new PrimitiveConstant(constant);
    }
    if (value instanceof StringReference stringReference) {
      return new StringConstant(stringReference.value());
    }
    if (value instanceof DfaAssistProvider.BoxedValue boxedValue) {
      DfConstantType<?> wrappedConstant = primitiveConstant(boxedValue.value());
      if (wrappedConstant != null) {
        return new ObjectWithSpecialField(boxedValue.type(), SpecialField.UNBOX, new PrimitiveConstant(wrappedConstant));
      }
    }
    if (value instanceof ObjectReference ref) {
      ReferenceType type = ref.referenceType();
      if (!classLoaderFilter.test(type.classLoader())) return null;
      String name = type.name();
      String enumConstantName = getEnumConstantName(ref);
      if (enumConstantName != null) {
        return new EnumConstant(type, enumConstantName);
      }
      if (fillSpecial) {
        if (value instanceof ArrayReference) {
          PrimitiveConstant length = new PrimitiveConstant(DfTypes.intValue(((ArrayReference)value).length()));
          return new ObjectWithSpecialField(type, SpecialField.ARRAY_LENGTH, length);
        }
        else if (name.startsWith("java.util.Collections$Empty")) {
          return collectionWithSize(type, 0);
        }
        else if (name.startsWith("java.util.Collections$Singleton")) {
          return collectionWithSize(type, 1);
        }
        else if (COLLECTIONS_WITH_SIZE_FIELD.contains(name)) {
          Value size = getField(ref, "size");
          if (size instanceof IntegerValue) {
            return collectionWithSize(type, ((IntegerValue)size).value());
          }
        }
        else if ("java.util.ImmutableCollections$ListN".equals(name)) {
          Value elements = getField(ref, "elements");
          if (elements instanceof ArrayReference) {
            return collectionWithSize(type, ((ArrayReference)elements).length());
          }
        }
        else if ("java.util.Arrays$ArrayList".equals(name)) {
          Value elements = getField(ref, "a");
          if (elements instanceof ArrayReference) {
            return collectionWithSize(type, ((ArrayReference)elements).length());
          }
        }
        else if (CommonClassNames.JAVA_UTIL_HASH_SET.equals(name) ||
                 "java.util.TreeSet".equals(name)) {
          Value map = getField(ref, CommonClassNames.JAVA_UTIL_HASH_SET.equals(name) ? "map" : "m");
          if (map instanceof ObjectReference) {
            Value size = getField((ObjectReference)map, "size");
            if (size instanceof IntegerValue) {
              return collectionWithSize(type, ((IntegerValue)size).value());
            }
          }
        }
        else if (TypeConversionUtil.isPrimitiveWrapper(name)) {
          DfConstantType<?> wrappedConstant = primitiveConstant(getField(ref, "value"));
          if (wrappedConstant != null) {
            return new ObjectWithSpecialField(type, SpecialField.UNBOX, new PrimitiveConstant(wrappedConstant));
          }
        }
        else if (CommonClassNames.JAVA_UTIL_OPTIONAL.equals(name)) {
          Value wrappedValue = getField(ref, "value");
          if (wrappedValue != null) {
            JdiValueInfo wrappedInfo = from(wrappedValue, classLoaderFilter, false);
            if (wrappedInfo != null) {
              return new ObjectWithSpecialField(type, SpecialField.OPTIONAL_VALUE, wrappedInfo);
            }
          }
        }
      }
      return new ObjectRef(type);
    }
    return null;
  }

  @NotNull
  private static ObjectWithSpecialField collectionWithSize(ReferenceType type, int size) {
    PrimitiveConstant length = new PrimitiveConstant(DfTypes.intValue(size));
    return new ObjectWithSpecialField(type, SpecialField.COLLECTION_SIZE, length);
  }

  private static @Nullable Value getField(@NotNull ObjectReference object, @NotNull String name) {
    Field field = DebuggerUtils.findField(object.referenceType(), name);
    if (field == null) return null;
    return object.getValue(field);
  }

  private static String getEnumConstantName(ObjectReference ref) {
    ReferenceType type = ref.referenceType();
    if (!(type instanceof ClassType) || !((ClassType)type).isEnum()) return null;
    ClassType superclass = ((ClassType)type).superclass();
    if (superclass == null) return null;
    if (!superclass.name().equals(CommonClassNames.JAVA_LANG_ENUM)) {
      superclass = superclass.superclass();
    }
    if (superclass == null || !superclass.name().equals(CommonClassNames.JAVA_LANG_ENUM)) return null;
    Field nameField = DebuggerUtils.findField(superclass, "name");
    if (nameField == null) return null;
    Value nameValue = ref.getValue(nameField);
    return nameValue instanceof StringReference ? ((StringReference)nameValue).value() : null;
  }

  private static @Nullable DfConstantType<?> primitiveConstant(@Nullable Value jdiValue) {
    if (jdiValue == DfaAssistProvider.NullConst) {
      return DfTypes.NULL;
    }
    if (jdiValue instanceof BooleanValue) {
      return DfTypes.booleanValue(((BooleanValue)jdiValue).value());
    }
    if (jdiValue instanceof LongValue) {
      return DfTypes.longValue(((LongValue)jdiValue).longValue());
    }
    if (jdiValue instanceof ShortValue || jdiValue instanceof CharValue ||
        jdiValue instanceof ByteValue || jdiValue instanceof IntegerValue) {
      return DfTypes.intValue(((PrimitiveValue)jdiValue).intValue());
    }
    if (jdiValue instanceof FloatValue) {
      return DfTypes.floatValue(((FloatValue)jdiValue).floatValue());
    }
    if (jdiValue instanceof DoubleValue) {
      return DfTypes.doubleValue(((DoubleValue)jdiValue).doubleValue());
    }
    return null;
  }
}
