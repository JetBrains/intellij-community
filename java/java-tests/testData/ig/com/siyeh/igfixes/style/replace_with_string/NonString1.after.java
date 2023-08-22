package com.siyeh.igfixes.style.replace_with_string;

class NonString1 {

  String foo(CharSequence text) {
    return String.valueOf(text); // no toString() because of NPEs
  }
}