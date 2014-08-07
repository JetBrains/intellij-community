/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.FilterOutputStream;
import java.io.IOException;

public class Example {
  void test() {

    if (<warning descr="Condition 'ArrayUtils.toMap(null) == null' is always 'true'">ArrayUtils.toMap(null) == null</warning>) {
      System.out.println("null");
    }

    if (<warning descr="Condition 'ClassUtils.getPackageName((String)null) == null' is always 'false'">ClassUtils.getPackageName((String)null) == null</warning>) {
      System.out.println("null");
    }

    @NotNull Class x = <warning descr="Expression 'ClassUtils.primitiveToWrapper(null)' might evaluate to null but is assigned to a variable that is annotated with @NotNull">ClassUtils.primitiveToWrapper(null)</warning>;
  }

  void writeBytes(@Nullable byte[] bytes) throws IOException {
    new FilterOutputStream(null).write(<warning descr="Argument 'bytes' might be null">bytes</warning>);
  }
}
