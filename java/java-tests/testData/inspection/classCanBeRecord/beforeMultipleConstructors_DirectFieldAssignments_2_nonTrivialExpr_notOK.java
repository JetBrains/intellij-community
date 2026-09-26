// "Convert to record class" "false"

class User<caret> {
  final String name;
  final int age;
  final String tag;

  User(String name, int age) {
    this.name = name;
    this.age = age / 2;
    this.tag = null;
  }

  User(String name, int age, String tag) {
    this.name = name;
    this.age = age / 2;
    this.tag = tag;
  }
}
