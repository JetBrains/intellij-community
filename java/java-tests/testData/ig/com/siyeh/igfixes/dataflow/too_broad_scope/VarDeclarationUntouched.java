package com.siyeh.igfixes.dataflow.too_broad_scope;

public class ForStatement {

  void noCondition() {
    var i<caret> = 1;
    for (; ; i++) {
      if (i == 10){
        break;
      }
    }
  }
}