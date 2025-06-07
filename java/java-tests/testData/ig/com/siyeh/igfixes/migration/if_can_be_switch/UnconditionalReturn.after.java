package com.siyeh.ipp.switchtoif.replace_if_with_switch;

public class Test {
  String test(Test object) {
      switch (object) {
          case Test test -> {
              return "bar";
          }
          case null -> {
          }
      }
    return "foo";
  }
}