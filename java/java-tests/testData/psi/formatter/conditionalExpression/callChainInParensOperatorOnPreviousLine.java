package org.example;

public class A {
  void f() {
    String x = true ? (new StringBuilder()
    .append("Hello")
                        .toString()) : (new StringBuilder()
                          .append("World")
            .toString());
  }
}