// "Escape trailing whitespaces" "true"

class Foo {
  void test() {
    String colors = """
      red\040\t<caret> 	 
      green
""";
  }
}
