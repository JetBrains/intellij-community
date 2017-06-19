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
package com.intellij.jvm.createClass.api

/**
 * Example: unresolved reference in the `import com.foo.Bar` in Java.
 *
 * This reference could be resolved into a class, interface, enum or annotation.
 * Java implementation would generate respective action for each JVM class kind.
 * In Groovy the interface could be generated from a trait, so Groovy implementation provides respective actions for generating
 * regular class kinds + action for generating a trait.
 * In Kotlin this reference could be also resolved into an Kotlin Object, so Kotlin should additionally provide Kotlin Object action.
 */
interface CreateJvmClassFactory {

  /**
   * @return list of actions which render a class using additional info
   */
  fun createActions(request: CreateClassRequest): List<CreateClassAction>
}
