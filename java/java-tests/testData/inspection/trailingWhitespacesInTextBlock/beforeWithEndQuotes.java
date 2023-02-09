// "Fix all 'Trailing whitespace in text block' problems in file" "true"
class Foo {
  void test() {
    String text = """
                text"<caret> """;
    String text2 = """
                " """;
    String text3 = """
                test\" """;
    String text4 = """
                test\\" """;
    String text5 = """
                \" """;
    String text6 = """
                \\" """;
    String text7 = """
                \\\" """;
    String text8 = """
                \\\\" """;
    String text9 = """
                \\\\\" """;
    String text10 = """
                \u005C" """;
    String text11 = """
                \u005C\u005C" """;
    String text12 = """
                \u005C\u005C\u005C" """;
    String text13 = """
                \u005C " """;
    String text14 = """
                \u005C\u005C\u005C\u005C" """;
  }
}
