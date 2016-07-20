/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.stubsHierarchy.impl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation exists for documentation purposes only.<p/>
 *
 * The value annotated with this annotation is used to save memory when storing mostly-singular or empty collections.
 * For empty collection, 'null' value is used. For one-element collection, the value is the single element. Otherwise,
 * an array is used. Possible component types are specified in the annotation value.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE_USE)
@interface CompactArray {
  Class[] value();
}

/**
 * int hash of a qualified name by {@link NameEnvironment}
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE_USE)
@interface QNameHash { }

/**
 * int hash of a qualified name part, produced by {@link NameEnvironment#hashIdentifier(String)}
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE_USE)
@interface ShortName { }
