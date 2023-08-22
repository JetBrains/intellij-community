// "Replace 'if else' with '&&'" "GENERIC_ERROR_OR_WARNING"
package com.siyeh.ipp.trivialif.replaceIfWithConditional;

class Test {
  boolean test(boolean a, boolean b, boolean c) {
      return a && (a || b || c);
  }
}