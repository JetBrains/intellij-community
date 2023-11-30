package com.siyeh.igfixes.style.replace_with_string;

class Array {
  String array(char[] cs) {
    return new <caret>StringBuilder().append(cs, 0, 10).toString();
  }
}
