class StartsWithPrimitive2 {

  String foo(String str) {
    return new Strin<caret>gBuilder().append(+1).append(str).append(';').toString();
  }
}