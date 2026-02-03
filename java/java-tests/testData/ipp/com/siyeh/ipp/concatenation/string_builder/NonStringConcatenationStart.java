package com.siyeh.ipp.concatenation.string_builder;

public class NonStringConcatenationStart {

  String foo() {
      return 1 + 2 <caret> //keep me
             + "asdf";
  }
}
