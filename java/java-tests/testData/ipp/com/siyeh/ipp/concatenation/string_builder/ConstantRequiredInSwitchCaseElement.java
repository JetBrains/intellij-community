package com.siyeh.ipp.concatenation.string_builder;

public class ConstantRequiredInSwitchCaseElement {
  void x(String s) {
    switch (s) {
      case "asdf" <caret>+ 1:
    }
  }
}