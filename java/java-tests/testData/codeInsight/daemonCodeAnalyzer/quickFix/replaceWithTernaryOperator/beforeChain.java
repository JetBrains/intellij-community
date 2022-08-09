// "Replace with 'integer != null ?:'" "true-preview"

import java.lang.Integer;

class A{
  void test(){
    Integer integer = null;
    int i = integer.to<caret>String().length();
  }
}