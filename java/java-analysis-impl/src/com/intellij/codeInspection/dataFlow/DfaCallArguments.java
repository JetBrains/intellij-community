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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.DfaValue;

import java.util.Arrays;
import java.util.Objects;

/* package */ final class DfaCallArguments {
  final DfaValue myQualifier;
  final DfaValue[] myArguments;
  final boolean myPure;

  public DfaCallArguments(DfaValue qualifier, DfaValue[] arguments, boolean pure) {
    myQualifier = qualifier;
    myArguments = arguments;
    myPure = pure;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DfaCallArguments)) return false;
    DfaCallArguments that = (DfaCallArguments)o;
    return myPure == that.myPure &&
           Objects.equals(myQualifier, that.myQualifier) &&
           Arrays.equals(myArguments, that.myArguments);
  }

  @Override
  public int hashCode() {
    return (Objects.hashCode(myQualifier) * 31 + Arrays.hashCode(myArguments))*31+Boolean.hashCode(myPure);
  }
}
