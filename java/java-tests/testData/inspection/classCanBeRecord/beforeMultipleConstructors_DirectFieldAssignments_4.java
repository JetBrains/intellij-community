// "Convert to record class" "true"

class User<caret> {
  final String name;
  final int age;
  final String tag;

  private static final int DEFAULT_AGE = 18;

  User(String name, int age) {
    this.name = name;
    this.age = DEFAULT_AGE;
    this.tag = null;
  }

  User(String name, int age, String tag) {
    this.name = name;
    this.age = age;
    this.tag = tag;
  }
}
