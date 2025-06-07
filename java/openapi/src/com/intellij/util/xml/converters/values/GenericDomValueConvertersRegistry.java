// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.converters.values;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
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
    Converter<?> getConverter();
    Condition<Pair<PsiType, GenericDomValue<?>>> getCondition();
  }

  private final Map<Condition<Pair<PsiType, GenericDomValue<?>>>, Converter<?>> myConditionConverters =
    new LinkedHashMap<>();

  public void registerDefaultConverters() {
    registerBooleanConverters();

    registerCharacterConverter();

    registerNumberValueConverters();

    registerClassValueConverters();
  }

  private void registerBooleanConverters() {
    registerConverter(new BooleanValueConverter(false), PsiTypes.booleanType());
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
    registerConverter(new CharacterValueConverter(false), PsiTypes.charType());
    registerConverter(new CharacterValueConverter(true), Character.class);
  }

  public void registerNumberValueConverters() {
    registerConverter(new NumberValueConverter<>(byte.class, false), PsiTypes.byteType());
    registerConverter(new NumberValueConverter<>(Byte.class, true), Byte.class);

    registerConverter(new NumberValueConverter<>(short.class, false), PsiTypes.shortType());
    registerConverter(new NumberValueConverter<>(Short.class, true), Short.class);

    registerConverter(new NumberValueConverter<>(int.class, false), PsiTypes.intType());
    registerConverter(new NumberValueConverter<>(Integer.class, true), Integer.class);

    registerConverter(new NumberValueConverter<>(long.class, false), PsiTypes.longType());
    registerConverter(new NumberValueConverter<>(Long.class, true), Long.class);

    registerConverter(new NumberValueConverter<>(float.class, false), PsiTypes.floatType());
    registerConverter(new NumberValueConverter<>(Float.class, true), Float.class);

    registerConverter(new NumberValueConverter<>(double.class, false), PsiTypes.doubleType());
    registerConverter(new NumberValueConverter<>(Double.class, true), Double.class);

    registerConverter(new NumberValueConverter<>(BigDecimal.class, true), BigDecimal.class);
    registerConverter(new NumberValueConverter<>(BigInteger.class, true), BigInteger.class);
  }

  public void registerConverter(@NotNull Converter<?> provider, final @NotNull PsiType type) {
    registerConverter(provider, pair -> Comparing.equal(pair.getFirst(), type));
  }

  public void registerConverter(@NotNull Converter<?> provider, @NotNull Condition<Pair<PsiType, GenericDomValue<?>>> condition) {
    myConditionConverters.put(condition, provider);
  }

  public final @Nullable Converter<?> getConverter(@NotNull GenericDomValue<?> domValue, @Nullable PsiType type) {
    final Pair<PsiType, GenericDomValue<?>> pair = Pair.create(type, domValue);
    final Converter<?> converter = getRegisteredConverter(pair);
    return converter != null?  converter : getCustomConverter(pair);
  }

  protected @Nullable Converter<?> getCustomConverter(Pair<PsiType, GenericDomValue<?>> pair) {
    return null;
  }

    protected @Nullable Converter<?> getRegisteredConverter(Pair<PsiType, GenericDomValue<?>> pair) {
    for (@NotNull Condition<Pair<PsiType, GenericDomValue<?>>> condition : myConditionConverters.keySet()) {
      if (condition.value(pair)) {
        return myConditionConverters.get(condition);
      }
    }
    return null;
  }

  public void registerConverter(@NotNull Converter<?> provider, @NotNull Class<?> type) {
    final String name = type.getCanonicalName();
    registerConverter(provider, pair -> pair.first != null && Objects.equals(name, pair.first.getCanonicalText()));
  }
}
