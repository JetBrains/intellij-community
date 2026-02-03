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
package com.intellij.application.options

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.java.JavaBundle
import com.intellij.openapi.options.BeanConfigurable

public class JavadocOptionsProvider : BeanConfigurable<CodeInsightSettings>(CodeInsightSettings.getInstance(),
                                                                            JavaBundle.message("javadoc.option.javadoc.title")) {

  init {
    checkBox(JavaBundle.message("javadoc.option.automatically.insert.closing.tag.javadoc"), instance::JAVADOC_GENERATE_CLOSING_TAG)
  }
}
