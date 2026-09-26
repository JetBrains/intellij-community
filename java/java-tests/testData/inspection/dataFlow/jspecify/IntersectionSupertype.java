/*
 * Copyright 2020 The JSpecify Authors.
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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

@NullMarked
interface IntersectionSupertype {
  <T extends Object & Lib> void x0(T t);

  <T extends @NullnessUnspecified Object & Lib> void x1(T t);

  <T extends @Nullable Object & Lib> void x2(T t);

  <T extends Object & @NullnessUnspecified Lib> void x3(T t);

  <T extends @NullnessUnspecified Object & @NullnessUnspecified Lib> void x4(T t);

  <T extends @Nullable Object & @NullnessUnspecified Lib> void x5(T t);

  <T extends Object & @Nullable Lib> void x6(T t);

  <T extends @NullnessUnspecified Object & @Nullable Lib> void x7(T t);

  <T extends @Nullable Object & @Nullable Lib> void x8(T t);

  default void useLib(Lib lib, int i) {
    switch (i) {
      case 0:
        x0(lib);
      case 1:
        x1(lib);
      case 2:
        x2(lib);
      case 3:
        x3(lib);
      case 4:
        x4(lib);
      case 5:
        x5(lib);
      case 6:
        x6(lib);
      case 7:
        x7(lib);
      case 8:
        x8(lib);
    }
  }

  default void useLibUnspec(@NullnessUnspecified Lib lib, int i) {
    switch (i) {
      case 0:
        // jspecify_nullness_not_enough_information
        x0(lib);
      case 1:
        // jspecify_nullness_not_enough_information
        x1(lib);
      case 2:
        // jspecify_nullness_not_enough_information
        x2(lib);
      case 3:
        // jspecify_nullness_not_enough_information
        x3(lib);
      case 4:
        // jspecify_nullness_not_enough_information
        x4(lib);
      case 5:
        // jspecify_nullness_not_enough_information
        x5(lib);
      case 6:
        // jspecify_nullness_not_enough_information
        x6(lib);
      case 7:
        // jspecify_nullness_not_enough_information
        x7(lib);
      case 8:
        this.<@Nullable Lib>x8(lib);
    }
  }

  default void useLibUnionNull(@Nullable Lib lib, int i) {
    switch (i) {
      case 0:
        // jspecify_nullness_mismatch
        x0(lib);
      case 1:
        // jspecify_nullness_mismatch
        x1(lib);
      case 2:
        // jspecify_nullness_mismatch
        x2(lib);
      case 3:
        // jspecify_nullness_mismatch
        x3(lib);
      case 4:
        // jspecify_nullness_not_enough_information
        x4(lib);
      case 5:
        // jspecify_nullness_not_enough_information
        x5(lib);
      case 6:
        // jspecify_nullness_mismatch
        x6(lib);
      case 7:
        // jspecify_nullness_not_enough_information
        x7(lib);
      case 8:
        x8(lib);
    }
  }

  interface Lib {}
}