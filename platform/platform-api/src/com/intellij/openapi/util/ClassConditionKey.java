/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import com.intellij.util.InstanceofCheckerGenerator;

/**
 * @author peter
 */
public class ClassConditionKey<T> {
  private final Condition<Object> myCondition;
  private final Class<T> myConditionClass;

  private ClassConditionKey(Class<T> aClass) {
    myCondition = InstanceofCheckerGenerator.getInstance().getInstanceofChecker(aClass);
    myConditionClass = aClass;
  }

  public static <T> ClassConditionKey<T> create(Class<T> aClass) {
    return new ClassConditionKey<>(aClass);
  }

  public boolean isInstance(Object o) {
    return myCondition.value(o);
  }

  @Override
  public String toString() {
    return myConditionClass.getName();
  }
}
