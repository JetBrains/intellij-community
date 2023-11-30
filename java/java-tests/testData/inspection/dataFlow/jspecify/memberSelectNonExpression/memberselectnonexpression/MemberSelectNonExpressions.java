/*
 * Copyright 2022 The JSpecify Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package memberselectnonexpression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

class MemberSelectNonExpressions {
  @NullMarked
  static class NullMarkedUsage {
    static void x0() {
      int x;

      Static.x();
      x = Static.x;

      MemberSelectNonExpressions.Static.x();
      x = MemberSelectNonExpressions.Static.x;

      memberselectnonexpression.MemberSelectNonExpressions.Static.x();
      x = memberselectnonexpression.MemberSelectNonExpressions.Static.x;
    }

    static void x1() {
      StaticParameterized<Object> s0;
      MemberSelectNonExpressions.StaticParameterized<Object> s1;
      memberselectnonexpression.MemberSelectNonExpressions.StaticParameterized<Object> s2;

      StaticParameterized<Object>.Inner i0;
      MemberSelectNonExpressions.StaticParameterized<Object>.Inner i1;
      memberselectnonexpression.MemberSelectNonExpressions.StaticParameterized<Object>.Inner i2;
    }
  }

  static class NotNullMarkedUsage {
    static void x0() {
      int x;

      Static.x();
      x = Static.x;

      MemberSelectNonExpressions.Static.x();
      x = MemberSelectNonExpressions.Static.x;

      memberselectnonexpression.MemberSelectNonExpressions.Static.x();
      x = memberselectnonexpression.MemberSelectNonExpressions.Static.x;
    }

    static void x1() {
      StaticParameterized<Object> s0;
      MemberSelectNonExpressions.StaticParameterized<Object> s1;
      memberselectnonexpression.MemberSelectNonExpressions.StaticParameterized<Object> s2;

      StaticParameterized<Object>.Inner i0;
      MemberSelectNonExpressions.StaticParameterized<Object>.Inner i1;
      memberselectnonexpression.MemberSelectNonExpressions.StaticParameterized<Object>.Inner i2;
    }
  }

  static class Static {
    static void x() {}

    static int x;
  }

  static class StaticParameterized<T extends @Nullable Object> {
    static void x() {}

    static int x;

    class Inner {}
  }
}
