package com.siyeh.igfixes.style.replace_with_string;

class NoConstructorArgument {
  void m() {

      String s = "appended" +
              "appended";
  }
}