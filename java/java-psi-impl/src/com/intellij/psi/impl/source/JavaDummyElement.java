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
package com.intellij.psi.impl.source;

import com.intellij.lang.java.parser.BasicJavaParserUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.tree.JavaElementTypeFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Dummy file element for using together with DummyHolder.
 * See {@link com.intellij.psi.impl.PsiJavaParserFacadeImpl} for details.
 */
public class JavaDummyElement extends BasicJavaDummyElement {
  public JavaDummyElement(@Nullable CharSequence text,
                          BasicJavaParserUtil.@NotNull ParserWrapper parser,
                          @NotNull LanguageLevel level) {
    super(text, parser, level, JavaElementTypeFactory.INSTANCE);
  }

  public JavaDummyElement(@Nullable CharSequence text,
                          BasicJavaParserUtil.@NotNull ParserWrapper parser,
                          @NotNull LanguageLevel level,
                          boolean consumeAll) {
    super(text, parser, level, JavaElementTypeFactory.INSTANCE, consumeAll);
  }
}