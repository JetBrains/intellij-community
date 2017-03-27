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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

class OptionalOrElse {
  @NotNull
  Boolean bug(@Nullable Boolean whatever) {
    return <warning descr="Expression 'Optional.ofNullable(whatever).orElse(null)' might evaluate to null but is returned by the method declared as @NotNull">Optional.ofNullable(whatever).orElse(null)</warning>;
  }

  @NotNull
  String nullable(Optional<String> opt) {
    return <warning descr="Expression 'opt.orElse(null)' might evaluate to null but is returned by the method declared as @NotNull">opt.orElse(null)</warning>;
  }

  @NotNull
  String ok(Optional<String> opt) {
    return opt.orElse("");
  }

  @NotNull
  String strangeButOk(Optional<String> opt) {
    if(opt.isPresent()) {
      return opt.orElse(null);
    } else {
      return "";
    }
  }

  void isPresentCheck(Optional<String> opt) {
    String result = opt.orElse(null);
    if(<warning descr="Condition 'result == null && opt.isPresent()' is always 'false'">result == null && <warning descr="Condition 'opt.isPresent()' is always 'false' when reached">opt.isPresent()</warning></warning>) {
      System.out.println("Impossible");
    }
  }
}