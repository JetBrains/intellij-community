// "Replace with 'integer != null ?:'" "true-preview"

import java.lang.Integer;

class A{
  void test(){
    Integer integer = Math.random() > 0.5 ? null : 1.0;
    int i = integer != null ? integer.intValue() : 0<caret>;
  }
}