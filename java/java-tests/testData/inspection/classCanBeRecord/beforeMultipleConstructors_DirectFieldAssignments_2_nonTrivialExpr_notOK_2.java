// "Convert to record class" "false"

class Foo<caret> {
  static int counter = 0;

  final String name;
  final int id;
  final String tag;

  Foo(String name) {
    this.name = name;
    this.id = counter++;
    this.tag = null;
  }

  Foo(String name, String tag) {
    this.name = name;
    this.id = counter++;
    this.tag = tag;
  }
}