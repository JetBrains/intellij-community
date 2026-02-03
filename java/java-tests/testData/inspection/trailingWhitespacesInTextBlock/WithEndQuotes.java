class Foo {
  void test() {
    String text = """
                text"<warning descr="Trailing whitespace characters inside text block"><caret> </warning>""";
    String text2 = """
                "<warning descr="Trailing whitespace characters inside text block"> </warning>""";
    String text3 = """
                test\"<warning descr="Trailing whitespace characters inside text block"> </warning>""";
    String text4 = """
                test\\"<warning descr="Trailing whitespace characters inside text block"> </warning>""";
    String text5 = """
                \"<warning descr="Trailing whitespace characters inside text block"> </warning>""";
    String text6 = """
                \\"<warning descr="Trailing whitespace characters inside text block"> </warning>""";
    String text7 = """
                \\\"<warning descr="Trailing whitespace characters inside text block"> </warning>""";
    String text8 = """
                \\\\"<warning descr="Trailing whitespace characters inside text block"> </warning>""";
    String text9 = """
                \\\\\"<warning descr="Trailing whitespace characters inside text block"> </warning>""";
    String text10 = """
                \u005C"<warning descr="Trailing whitespace characters inside text block"> </warning>""";
    String text11 = """
                \u005C\u005C"<warning descr="Trailing whitespace characters inside text block"> </warning>""";
    String text12 = """
                \u005C\u005C\u005C"<warning descr="Trailing whitespace characters inside text block"> </warning>""";
    String text13 = """
                <error descr="Illegal escape character in string literal">\u005C</error> "<warning descr="Trailing whitespace characters inside text block"> </warning>""";
    String text14 = """
                \u005C\u005C\u005C\u005C"<warning descr="Trailing whitespace characters inside text block"> </warning>""";
    String text15 = STR."""
      "scary"\{}<warning descr="Trailing whitespace characters inside text block"> </warning>
      "scary"\{} 
      "scary" \{} 
      """;
  }
}
