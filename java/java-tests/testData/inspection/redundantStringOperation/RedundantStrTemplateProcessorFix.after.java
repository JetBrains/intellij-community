// "Fix all 'Redundant 'String' operation' problems in file" "true"
import static java.lang.StringTemplate.RAW;

class Foo {
  void test() {
    String str1 = """
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

    String str3 = "Simple string";
    String str4 = StringTemplate.STR."Simple \{template}";

    String str5 = STR."Simple \{template} " + "Simple string" + "str";

    StringTemplate str6 = RAW."Hi!";
  }
}