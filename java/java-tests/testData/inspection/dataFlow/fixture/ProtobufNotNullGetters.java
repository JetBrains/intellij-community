package com.google.protobuf;

abstract class GeneratedMessage{}

class Msg extends com.google.protobuf.GeneratedMessage {
  private String myString;
  
  public String getString() {
    return myString;
  } 
}
class Use {
  void test(Msg msg) {
    if (<warning descr="Condition 'msg.getString() == null' is always 'false'">msg.getString() == null</warning>) {
      
    }
  }
}