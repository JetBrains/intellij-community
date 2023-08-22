package com.siyeh.igfixes.style.replace_with_string;

public class SimpleStringBuffer {
  String foo() {
    return new <caret>StringBuffer().toString();
  }
}
