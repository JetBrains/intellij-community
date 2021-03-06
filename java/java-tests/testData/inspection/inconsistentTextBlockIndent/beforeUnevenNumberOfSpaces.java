// "Replace spaces with tabs (4 spaces = 1 tab)" "false"

class Foo {
  void test() {
     String colors = """
     red
<caret>	   green
     blue""";
  }
}