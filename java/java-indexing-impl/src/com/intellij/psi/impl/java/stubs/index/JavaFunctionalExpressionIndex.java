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

/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.impl.java.stubs.FunctionalExpressionKey;
import com.intellij.psi.stubs.AbstractStubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class JavaFunctionalExpressionIndex extends AbstractStubIndex<FunctionalExpressionKey, PsiFunctionalExpression> {
  private static final KeyDescriptor<FunctionalExpressionKey> KEY_DESCRIPTOR = new KeyDescriptor<FunctionalExpressionKey>() {
    @Override
    public int getHashCode(FunctionalExpressionKey value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(FunctionalExpressionKey val1, FunctionalExpressionKey val2) {
      return val1.equals(val2);
    }

    @Override
    public void save(@NotNull DataOutput out, FunctionalExpressionKey value) throws IOException {
      value.serializeKey(out);
    }

    @Override
    public FunctionalExpressionKey read(@NotNull DataInput in) throws IOException {
      return FunctionalExpressionKey.deserializeKey(in);
    }
  };

  @NotNull
  @Override
  public KeyDescriptor<FunctionalExpressionKey> getKeyDescriptor() {
    return KEY_DESCRIPTOR;
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @NotNull
  @Override
  public StubIndexKey<FunctionalExpressionKey, PsiFunctionalExpression> getKey() {
    return JavaStubIndexKeys.FUNCTIONAL_EXPRESSIONS;
  }

}