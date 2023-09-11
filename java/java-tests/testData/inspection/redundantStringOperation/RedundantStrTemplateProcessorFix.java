// "Fix all 'Redundant 'String' operation' problems in file" "true"
class Foo {
  void test() {
    String str1 = <warning descr="'STR' template processor can be converted to a string literal">STR<caret></warning>."""
      my
      long
      simle
      string
      """;

    String template = "template";
    String str2 = STR."""
      my
      long
      \{template}
      string
      """;

    String str3 = <warning descr="'STR' template processor can be converted to a string literal">StringTemplate.STR</warning>."Simple string";
    String str4 = StringTemplate.STR."Simple \{template}";

    String str5 = STR."Simple \{template} " + <warning descr="'STR' template processor can be converted to a string literal">STR</warning>."Simple string" + "str";
  }
}