class Test {
  {
    String s = "";
    s += new StringBuilder().<caret>append("foo").append("bar").toString();
  }
}