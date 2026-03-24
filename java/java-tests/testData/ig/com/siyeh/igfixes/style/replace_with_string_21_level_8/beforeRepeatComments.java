// "Replace 'StringBuilder' with 'String'" "true"

class Repeat {
  String foo() {
    StringBuilder <caret>sb = new StringBuilder();
    /*1*/sb.repeat(/*2*/"abc"/*3*/,/*4*/100/*5*/)/*6*/;/*7*/
    return sb.toString();
  }
}
