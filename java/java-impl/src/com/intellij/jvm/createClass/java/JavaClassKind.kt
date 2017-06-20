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
package com.intellij.jvm.createClass.java

import com.intellij.icons.AllIcons
import com.intellij.jvm.createClass.SourceClassKind
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import javax.swing.Icon

/**
 * Java source class kinds has one-to-one mapping to JVM class kinds.
 */
enum class JavaClassKind(
  override val icon: Icon,
  override val displayName: String
) : SourceClassKind {

  CLASS(AllIcons.Nodes.Class, "Class"),
  INTERFACE(AllIcons.Nodes.Interface, "Interface"),
  ANNOTATION(AllIcons.Nodes.Annotationtype, "Annotation"),
  ENUM(AllIcons.Nodes.Enum, "Enum");

  override val language: Language get() = JavaLanguage.INSTANCE
}
