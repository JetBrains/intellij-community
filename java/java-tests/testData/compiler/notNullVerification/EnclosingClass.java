// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import org.jetbrains.annotations.NotNull;

public class EnclosingClass {
  public static Object fromStatic() {
    return new Object() {
      void foo(@NotNull String s) { }
    };
  }

  public Object fromInstance() {
    return new Object() {
      boolean foo(@NotNull String s) {
        return s.contains(EnclosingClass.this.toString());
      }
    };
  }
}