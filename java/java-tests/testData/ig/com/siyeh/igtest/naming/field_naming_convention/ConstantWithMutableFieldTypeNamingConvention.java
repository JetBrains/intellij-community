package com.siyeh.igtest.naming.constant_naming_convention;

import java.util.*;

class ConstantNamingConvention {
  static final List<String> unmodifiableList = Collections.unmodifiableList(new ArrayList<>());
  static final MyImmutable immutable = new MyImmutable(0, "name");
}

final class MyImmutable {
  private final int idx;
  private final String name;

  MyImmutable(int idx, String name) {
    this.idx = idx;
    this.name = name;
  }
}