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
package com.intellij.codeInsight.unwrap;

/**
 * @author Konstantin Bulenkov
 */
public class JavaUnwrapDescriptor extends UnwrapDescriptorBase {
  @Override
  protected Unwrapper[] createUnwrappers() {
    return new Unwrapper[]{
      new JavaArrayInitializerUnwrapper(),
      new JavaMethodParameterUnwrapper(),
      new JavaElseUnwrapper(),
      new JavaElseRemover(),
      new JavaIfUnwrapper(),
      new JavaWhileUnwrapper(),
      new JavaForUnwrapper(),
      new JavaBracesUnwrapper(),
      new JavaTryUnwrapper(),
      new JavaCatchRemover(),
      new JavaSynchronizedUnwrapper(),
      new JavaAnonymousUnwrapper(),
      new JavaConditionalUnwrapper(),
      new JavaPolyadicExpressionUnwrapper()
    };
  }
}
