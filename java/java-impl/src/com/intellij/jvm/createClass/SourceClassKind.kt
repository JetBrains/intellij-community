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
package com.intellij.jvm.createClass

import com.intellij.lang.Language
import javax.swing.Icon

/**
 * This class represents class kind in sources of particular JVM language.
 * Each JVM language has its own kinds of sources.
 * The mapping between what-would-be a JVM class and a source kind is provided via [CreateJvmClassFactory.getSourceKinds].
 *
 * Some class kinds are common between languages, e.g. annotation.
 * Given a situation where only annotations could be generated we still get two comboboxes in the dialog,
 * first one for language, and the second (kind) combobox will contain the only option to generate an annotation for each language.
 *
 * The natural desire is to hide the second combobox.
 * But we don't want to mix language class types, i.e. inherit Kotlin ones from Java ones.
 *
 * The similarity between such class kinds is represented by display name.
 * In above cast the title of the dialog will be changed to 'Create Annotation %name%'.
 * This also opens extensibility to merge kinds not present in Java at all, such as Traits.
 */
interface SourceClassKind {

  val language: Language

  val icon: Icon?

  val displayName: String
}
