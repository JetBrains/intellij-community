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

import com.intellij.psi.util.ClassKind;
import org.jetbrains.annotations.NonNls;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * To be used together with {@link com.intellij.util.xml.ExtendClass}.
 *
 * If specified, a 'create from usage' quick fix will create class based on the {@link #value()} template.
 *
 * @author peter
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ClassTemplate {
  @NonNls String value() default "";

  /**
   * @return affects the quick fix presentable text, 'Create class ...' or 'Create interface ...', etc.
   */
  ClassKind kind() default ClassKind.CLASS;

}
