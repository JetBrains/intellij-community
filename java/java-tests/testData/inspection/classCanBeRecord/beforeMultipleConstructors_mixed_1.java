// "Convert to record class" "true-preview"
class Person<caret> {
  final String name;
  final int age;

  Person(String name, int age) {
    this.name = name;
    this.age = age;
  }

  Person(String name) {
    this.name = name;
    this.age = 42;
  }

  Person(int age) {
    this("Unknown", age);
  }
}
