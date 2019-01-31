// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import org.jetbrains.annotations.NotNull

@SuppressWarnings("unused")
enum GroovyEnum {
  Value(null, "1");

  GroovyEnum(String s1, @NotNull String s2) { }
}