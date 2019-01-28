// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
      new JavaLambdaUnwrapper(),
      new JavaConditionalUnwrapper(),
      new JavaPolyadicExpressionUnwrapper(),
      new JavaSwitchExpressionUnwrapper(),
      new JavaSwitchStatementUnwrapper()
    };
  }
}
