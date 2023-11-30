package com.siyeh.igfixes.dataflow.too_broad_scope;

public class ForStatement2 {
  void noCondition() {
      for (int i = 1; ; ) {
      i++;
      if (i == 10){
        break;
      }
    }
  }
}