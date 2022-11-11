// "Escape trailing whitespace characters" "true"

class Foo {
  void test() {
    String colors = """
      red\040\t\040\t\040
      green
""";
  }
}
