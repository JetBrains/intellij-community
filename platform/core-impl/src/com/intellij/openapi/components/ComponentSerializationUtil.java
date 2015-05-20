/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.components;

import com.intellij.util.ReflectionUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * @author nik
 */
public class ComponentSerializationUtil {
  @NotNull
  public static <S> Class<S> getStateClass(@NotNull Class<? extends PersistentStateComponent> aClass) {
    TypeVariable<Class<PersistentStateComponent>> variable = PersistentStateComponent.class.getTypeParameters()[0];
    Type type = ReflectionUtil.resolveVariableInHierarchy(variable, aClass);
    assert type != null : aClass;
    @SuppressWarnings("unchecked") Class<S> result = (Class<S>)ReflectionUtil.getRawType(type);
    return result;
  }

  public static <S> void loadComponentState(@NotNull PersistentStateComponent<S> configuration, @Nullable Element element) {
    if (element != null) {
      Class<S> stateClass = getStateClass(configuration.getClass());
      @SuppressWarnings("unchecked") S state = stateClass.equals(Element.class) ? (S)element : XmlSerializer.deserialize(element, stateClass);
      if (state != null) {
        configuration.loadState(state);
      }
    }
  }
}
