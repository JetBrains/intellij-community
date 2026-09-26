/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.xml;

import java.lang.annotation.Annotation;

@SuppressWarnings({"ClassExplicitlyAnnotation"})
public abstract class ExtendClassImpl implements ExtendClass {

  @Override
  public boolean instantiatable() {
    return false;
  }

  @Override
  public boolean canBeDecorator() {
    return false;
  }

  @Override
  public boolean allowEmpty() {
    return false;
  }

  @Override
  public boolean allowNonPublic() {
    return false;
  }

  @Override
  public boolean allowAbstract() {
    return true;
  }

  @Override
  public boolean allowInterface() {
    return true;
  }

  @Override
  public boolean allowEnum() {
    return true;
  }

  @Override
  public Class<? extends Annotation> annotationType() {
    return ExtendClass.class;
  }

  @Override
  public boolean jvmFormat() {
    return true;
  }
}
