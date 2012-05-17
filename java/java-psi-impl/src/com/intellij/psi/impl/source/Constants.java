/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.TokenSet;

public interface Constants extends ElementType {
  TokenSet CLASS_BIT_SET = TokenSet.create(JavaElementType.CLASS, JavaElementType.ANONYMOUS_CLASS, JavaElementType.ENUM_CONSTANT_INITIALIZER);
  TokenSet FIELD_BIT_SET = TokenSet.create(JavaElementType.FIELD, JavaElementType.ENUM_CONSTANT);
  TokenSet METHOD_BIT_SET = TokenSet.create(JavaElementType.METHOD, JavaElementType.ANNOTATION_METHOD);
  TokenSet CLASS_INITIALIZER_BIT_SET = TokenSet.create(JavaElementType.CLASS_INITIALIZER);
  TokenSet PARAMETER_BIT_SET = TokenSet.create(JavaElementType.PARAMETER);
  TokenSet CATCH_SECTION_BIT_SET = TokenSet.create(JavaElementType.CATCH_SECTION);
  TokenSet JAVA_CODE_REFERENCE_BIT_SET = TokenSet.create(JavaElementType.JAVA_CODE_REFERENCE);
  TokenSet NAME_VALUE_PAIR_BIT_SET = TokenSet.create(JavaElementType.NAME_VALUE_PAIR);
  TokenSet ANNOTATION_BIT_SET = TokenSet.create(JavaElementType.ANNOTATION);
}
