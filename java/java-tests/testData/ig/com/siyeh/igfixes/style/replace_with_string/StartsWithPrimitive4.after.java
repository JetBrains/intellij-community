class StartsWithPrimitive2 {

  String foo(String str) {
    return String.va<caret>lueOf(+1) + 0 + str + ';';
  }
}