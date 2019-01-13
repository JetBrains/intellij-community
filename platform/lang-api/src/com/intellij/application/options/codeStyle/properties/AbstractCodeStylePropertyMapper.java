// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractCodeStylePropertyMapper {
  private @NotNull final CodeStyleSettings myRootSettings;
  private final AtomicNotNullLazyValue<Map<String,CodeStylePropertyAccessor>> myAccessorMap;

  public AbstractCodeStylePropertyMapper(@NotNull CodeStyleSettings settings) {
    myRootSettings = settings;
    myAccessorMap = AtomicNotNullLazyValue.createValue(() -> createMap());
  }

  public boolean setProperty(@NotNull String name, @NotNull String value) {
    if (getAccessorMap().containsKey(name)) {
      return myAccessorMap.getValue().get(name).set(value);
    }
    return false;
  }

  @Nullable
  public String getProperty(@NotNull String name) {
    if (getAccessorMap().containsKey(name)) {
      return getAccessorMap().get(name).get();
    }
    return null;
  }

  public List<String> enumProperties() {
    return getAccessorMap().keySet().stream().sorted().collect(Collectors.toList());
  }

  public List<String> enumPropertiesFor(@NotNull Class... codeStyleClass) {
    final Map<String, CodeStylePropertyAccessor> accessorMap = myAccessorMap.getValue();
    return accessorMap.keySet().stream().filter(name -> {
      CodeStylePropertyAccessor accessor = accessorMap.get(name);
      for (Class aClass : codeStyleClass) {
        if (accessor.getObjectClass().equals(aClass)) {
          return true;
        }
      }
      return false;
    }).sorted().collect(Collectors.toList());
  }

  private Map<String, CodeStylePropertyAccessor> createMap() {
    Map<String, CodeStylePropertyAccessor> accessorMap = ContainerUtil.newHashMap();
    for (CodeStyleObjectDescriptor descriptor : getSupportedFields()) {
      addAccessorsFor(accessorMap, descriptor.getCodeStyleObject(), descriptor.getSupportedFields());
    }
    return accessorMap;
  }

  @NotNull
  protected abstract List<CodeStyleObjectDescriptor> getSupportedFields();

  private void addAccessorsFor(@NotNull Map<String, CodeStylePropertyAccessor> accessorMap,
                               @NotNull Object codeStyleObject,
                               @Nullable Set<String> supportedFields) {
    Class codeStyleClass = codeStyleObject.getClass();
    for (Field field : getCodeStyleFields(codeStyleClass)) {
      String fieldName = field.getName();
      if (supportedFields == null || supportedFields.contains(fieldName)) {
        final CodeStylePropertyAccessor accessor = getAccessor(codeStyleObject, field);
        if (accessor != null) {
          accessorMap.put(accessor.getPropertyName(), accessor);
        }
      }
    }
  }

  @Nullable
  protected CodeStylePropertyAccessor getAccessor(@NotNull Object codeStyleObject, @NotNull Field field) {
    return new PropertyAccessorFactory(field).createAccessor(codeStyleObject);
  }

  private List<Field> getCodeStyleFields(Class codeStyleClass) {
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

  @NotNull
  protected CodeStyleSettings getRootSettings() {
    return myRootSettings;
  }

  @NotNull
  private Map<String,CodeStylePropertyAccessor> getAccessorMap() {
    return myAccessorMap.getValue();
  }

  protected static final class CodeStyleObjectDescriptor {
    private final Object myObject;
    private final Set<String> mySupportedFields;

    CodeStyleObjectDescriptor(@NotNull Object codeStyleObject, @Nullable Set<String> fields) {
      myObject = codeStyleObject;
      mySupportedFields = fields;
    }

    @NotNull
    private Object getCodeStyleObject() {
      return myObject;
    }

    @Nullable
    private Set<String> getSupportedFields() {
      return mySupportedFields;
    }
  }

  public CodeStylePropertyAccessor getAccessor(@NotNull String property) {
    return myAccessorMap.getValue().get(property);
  }

  protected boolean useDeclaredFields() {
    return false;
  }

  @NotNull
  public abstract String getLanguageDomainId();

  public boolean containsProperty(@NotNull String name) {
    return getAccessorMap().containsKey(name);
  }
}
