package com.siyeh.igfixes.dataflow.too_broad_scope;

public class ForStatement2 {
  void noCondition() {
    int i<caret> = 1;
    for (; ; ) {
      i++;
      if (i == 10){
        break;
      }
    }
  }
}