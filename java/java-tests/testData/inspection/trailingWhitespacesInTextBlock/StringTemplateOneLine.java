class StringTemplateOneLine {

  public void foo(String type) {
    String s = STR."""
            Hello \{type}<warning descr="Trailing whitespace characters inside text block"><caret>    </warning>""";
  }
}