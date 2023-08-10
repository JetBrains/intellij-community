package com.siyeh.igfixes.dataflow.too_broad_scope;

public class ForStatement {

  void noCondition() {
      for (int i = 1; ; i++) {
      if (i == 10){
        break;
      }
    }
  }
}