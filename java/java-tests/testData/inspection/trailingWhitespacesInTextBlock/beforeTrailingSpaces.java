// "Remove trailing whitespace characters" "true"

class Foo {
  void test() {
    String colors = """
      red<caret>      
      green   
""";
  }
}

