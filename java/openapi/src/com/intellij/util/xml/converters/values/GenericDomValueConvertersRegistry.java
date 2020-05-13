// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.converters.values;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class GenericDomValueConvertersRegistry {

  public interface Provider {
    Converter getConverter();
    Condition<Pair<PsiType, GenericDomValue>> getCondition();
  }

  private final Map<Condition<Pair<PsiType, GenericDomValue>>, Converter<?>> myConditionConverters =
    new LinkedHashMap<>();

  public void registerDefaultConverters() {
    registerBooleanConverters();

    registerCharacterConverter();

    registerNumberValueConverters();

    registerClassValueConverters();
  }

  private void registerBooleanConverters() {
    registerConverter(new BooleanValueConverter(false), PsiType.BOOLEAN);
    registerConverter(new BooleanValueConverter(true), Boolean.class);
  }

  public void registerClassValueConverters() {
    registerConverter(ClassValueConverter.getClassValueConverter(), pair -> {
      PsiType psiType = pair.getFirst();
      if (psiType instanceof PsiClassType) {
        PsiClass resolve = ((PsiClassType)psiType).resolve();
        if (resolve != null) {
          return CommonClassNames.JAVA_LANG_CLASS.equals(resolve.getQualifiedName());
        }
      }
      return false;
    });
    registerConverter(ClassArrayConverter.getClassArrayConverter(), Class[].class);
  }

  public void registerCharacterConverter() {
    registerConverter(new CharacterValueConverter(false), PsiType.CHAR);
    registerConverter(new CharacterValueConverter(true), Character.class);
  }

  public void registerNumberValueConverters() {
    registerConverter(new NumberValueConverter(byte.class, false), PsiType.BYTE);
    registerConverter(new NumberValueConverter(Byte.class, true), Byte.class);

    registerConverter(new NumberValueConverter(short.class, false), PsiType.SHORT);
    registerConverter(new NumberValueConverter(Short.class, true), Short.class);

    registerConverter(new NumberValueConverter(int.class, false), PsiType.INT);
    registerConverter(new NumberValueConverter(Integer.class, true), Integer.class);

    registerConverter(new NumberValueConverter(long.class, false), PsiType.LONG);
    registerConverter(new NumberValueConverter(Long.class, true), Long.class);

    registerConverter(new NumberValueConverter(float.class, false), PsiType.FLOAT);
    registerConverter(new NumberValueConverter(Float.class, true), Float.class);

    registerConverter(new NumberValueConverter(double.class, false), PsiType.DOUBLE);
    registerConverter(new NumberValueConverter(Double.class, true), Double.class);

    registerConverter(new NumberValueConverter(BigDecimal.class, true), BigDecimal.class);
    registerConverter(new NumberValueConverter(BigInteger.class, true), BigInteger.class);
  }

  public void registerConverter(@NotNull Converter<?> provider, @NotNull final PsiType type) {
    registerConverter(provider, pair -> Comparing.equal(pair.getFirst(), type));
  }

  public void registerConverter(@NotNull Converter<?> provider, @NotNull Condition<Pair<PsiType, GenericDomValue>> condition) {
    myConditionConverters.put(condition, provider);
  }

  @Nullable
  public final Converter<?> getConverter(@NotNull GenericDomValue domValue, @Nullable PsiType type) {
    final Pair<PsiType, GenericDomValue> pair = Pair.create(type, domValue);
    final Converter<?> converter = getRegisteredConverter(pair);
    return converter != null?  converter : getCustomConverter(pair);
  }

  @Nullable
  protected Converter<?> getCustomConverter(Pair<PsiType, GenericDomValue> pair) {
    return null;
  }

    @Nullable
  protected Converter<?> getRegisteredConverter(Pair<PsiType, GenericDomValue> pair) {
    for (@NotNull Condition<Pair<PsiType, GenericDomValue>> condition : myConditionConverters.keySet()) {
      if (condition.value(pair)) {
        return myConditionConverters.get(condition);
      }
    }
    return null;
  }

  public void registerConverter(@NotNull Converter<?> provider, @NotNull Class type) {
    final String name = type.getCanonicalName();
    registerConverter(provider, pair -> pair.first != null && Objects.equals(name, pair.first.getCanonicalText()));
  }
}
