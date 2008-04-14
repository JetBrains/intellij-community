package com.intellij.util.xml.converters.values;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiType;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ConverterManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User: Sergey.Vasiliev
 */
public class GenericDomValueConvertersRegistry {

  private final Map<Condition<Pair<PsiType, GenericDomValue>>, Converter<?>> myConditionConverters =
    new LinkedHashMap<Condition<Pair<PsiType, GenericDomValue>>, Converter<?>>();

  private final Project myProject;

  public GenericDomValueConvertersRegistry(final Project project) {
    myProject = project;
  }

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
    final ConverterManager converterManager = DomManager.getDomManager(myProject).getConverterManager();
    final ClassValueConverter classValueConverter = ClassValueConverter.getClassValueConverter(myProject);
    converterManager.registerConverterImplementation(ClassValueConverter.class, classValueConverter);
    registerConverter(classValueConverter, Class.class);
    final ClassArrayConverter classArrayConverter = ClassArrayConverter.getClassArrayConverter(myProject);
    converterManager.registerConverterImplementation(ClassArrayConverter.class, classArrayConverter);
    registerConverter(classArrayConverter, Class[].class);
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
    registerConverter(provider, new Condition<Pair<PsiType, GenericDomValue>>() {
      public boolean value(final Pair<PsiType, GenericDomValue> pair) {
        return Comparing.equal(pair.getFirst(), type);
      }
    });
  }

  public void registerConverter(@NotNull Converter<?> provider, @NotNull Condition<Pair<PsiType, GenericDomValue>> condition) {
    myConditionConverters.put(condition, provider);
  }

  @Nullable
  public synchronized Converter<?> getConverter(@NotNull GenericDomValue domValue, @Nullable PsiType type) {
    final Pair<PsiType, GenericDomValue> pair = new Pair<PsiType, GenericDomValue>(type, domValue);
    for (@NotNull Condition<Pair<PsiType, GenericDomValue>> condition : myConditionConverters.keySet()) {
      if (condition.value(pair)) {
        return myConditionConverters.get(condition);
      }
    }
    return null;
  }

  public void registerConverter(@NotNull Converter<?> provider, @NotNull Class type) {
    final String name = type.getCanonicalName();
    registerConverter(provider, new Condition<Pair<PsiType, GenericDomValue>>() {
      public boolean value(final Pair<PsiType, GenericDomValue> pair) {
        return pair.first != null && Comparing.equal(name, pair.first.getCanonicalText());
      }
    });
  }

}
