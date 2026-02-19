package com.siyeh.igtest.imports.static_import_method_allowed;

import static java.lang.Math.abs;

class Simple {

  void f0o() {
    abs(1.0);
  }
}