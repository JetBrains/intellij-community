// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class AbstractCodeStylePropertyMapper {
  private final @NotNull CodeStyleSettings myRootSettings;
  private final Supplier<Map<String, CodeStylePropertyAccessor<?>>> myAccessorMap;

  public AbstractCodeStylePropertyMapper(@NotNull CodeStyleSettings settings) {
    myRootSettings = settings;
    myAccessorMap = new SynchronizedClearableLazy<>(this::createMap);
  }

  public List<String> enumProperties() {
    return getAccessorMap().keySet().stream().sorted().collect(Collectors.toList());
  }

  private Map<String, CodeStylePropertyAccessor<?>> createMap() {
    Map<String, CodeStylePropertyAccessor<?>> accessorMap = CollectionFactory.createSmallMemoryFootprintMap();
    for (CodeStyleObjectDescriptor descriptor : getSupportedFields()) {
      addAccessorsFor(accessorMap, descriptor.getCodeStyleObject(), descriptor.getSupportedFields());
    }
    addAdditionalAccessors(accessorMap);
    return accessorMap;
  }

  protected abstract @NotNull List<CodeStyleObjectDescriptor> getSupportedFields();

  protected void addAdditionalAccessors(@NotNull Map<String, CodeStylePropertyAccessor<?>> accessorMap) {
  }

  private void addAccessorsFor(@NotNull Map<String, CodeStylePropertyAccessor<?>> accessorMap,
                               @NotNull Object codeStyleObject,
                               @Nullable Set<String> supportedFields) {
    Class<?> codeStyleClass = getObjectStorageClass(codeStyleObject);
    for (Field field : getCodeStyleFields(codeStyleClass)) {
      String fieldName = field.getName();
      if (supportedFields == null || supportedFields.contains(fieldName)) {
        final CodeStylePropertyAccessor<?> accessor = getAccessor(codeStyleObject, field);
        if (accessor != null && !accessor.isIgnorable()) {
          accessorMap.put(accessor.getPropertyName(), accessor);
        }
      }
    }
  }

  private static Class<?> getObjectStorageClass(@NotNull Object codeStyleObject) {
    Class<?> objectClass = codeStyleObject.getClass();
    if (CodeStyleSettings.class.isAssignableFrom(objectClass)) {
      return CodeStyleSettings.class;
    }
    return objectClass;
  }

  protected @Nullable CodeStylePropertyAccessor<?> getAccessor(@NotNull Object codeStyleObject, @NotNull Field field) {
    return new FieldAccessorFactory(field).createAccessor(codeStyleObject);
  }

  private List<Field> getCodeStyleFields(Class<?> codeStyleClass) {
    List<Field> fields = new ArrayList<>();
    Field[] allFields = useDeclaredFields() ? codeStyleClass.getDeclaredFields() : codeStyleClass.getFields();
    for (Field field : allFields) {
      if (isPublic(field) && !isFinal(field)) {
        fields.add(field);
      }
    }
    return fields;
  }

  private static boolean isPublic(final Field field) {
    return (field.getModifiers() & Modifier.PUBLIC) != 0;
  }

  private static boolean isFinal(final Field field) {
    return (field.getModifiers() & Modifier.FINAL) != 0;
  }

  protected @NotNull CodeStyleSettings getRootSettings() {
    return myRootSettings;
  }

  private @NotNull Map<String,CodeStylePropertyAccessor<?>> getAccessorMap() {
    return myAccessorMap.get();
  }

  protected static final class CodeStyleObjectDescriptor {
    private final Object myObject;
    private final Set<String> mySupportedFields;

    public CodeStyleObjectDescriptor(@NotNull Object codeStyleObject, @Nullable Set<String> fields) {
      myObject = codeStyleObject;
      mySupportedFields = fields;
    }

    private @NotNull Object getCodeStyleObject() {
      return myObject;
    }

    private @Nullable @Unmodifiable Set<String> getSupportedFields() {
      return mySupportedFields;
    }
  }

  public CodeStylePropertyAccessor<?> getAccessor(@NotNull String property) {
    return myAccessorMap.get().get(property);
  }

  protected boolean useDeclaredFields() {
    return false;
  }

  public abstract @NotNull String getLanguageDomainId();

  public abstract @Nullable String getPropertyDescription(@NotNull String externalName);
}
