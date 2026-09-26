class StartsWithPrimitive {

  String foo(int i) {
    return new Stri<caret>ngBuffer().append(i).toString();
  }
}