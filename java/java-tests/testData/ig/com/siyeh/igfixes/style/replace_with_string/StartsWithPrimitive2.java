class StartsWithPrimitive2 {

  String foo(String str) {
    return new Strin<caret>gBuilder().append('L').append(str).append(';').toString();
  }
}