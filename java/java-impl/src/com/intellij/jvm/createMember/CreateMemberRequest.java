/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.jvm.createMember;

import org.jetbrains.annotations.NotNull;

/**
 * This class serves as WHAT in the API.
 * The WHAT instances are created by call site.
 * <p>
 * Example 1.
 * <p>
 * In Groovy foo.bar() will be resolvable if bar() would exist in Foo class (regardless of language Foo is written with).
 * Also it will be resolvable if Foo class would contain getBar() method which returns a Closure.
 * <p>
 * So Groovy site creates two respective requests:
 * <ul>
 * <li>Create method 'bar</li>
 * <li>Create method 'getBar()' with return type 'Closure'</li>
 * </ul>
 */
public interface CreateMemberRequest {

  /**
   * Used as intention text.
   *
   * @return request title.
   * Examples:
   * Create method 'foo'
   * Create property 'bar'
   * Create field 'baz'
   */
  @NotNull
  String getTitle();
}
