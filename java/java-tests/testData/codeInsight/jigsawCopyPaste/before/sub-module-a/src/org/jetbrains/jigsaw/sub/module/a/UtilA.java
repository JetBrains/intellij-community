package org.jetbrains.jigsaw.sub.module.a;

import org.jetbrains.jigsaw.sub.module.b.Api;

import java.util.HashSet;
import java.util.Set;

public class UtilA {
  public static Set<Api> get() {
    return new HashSet<>();
  }
}
