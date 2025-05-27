// "Convert to record class" "true-preview"
class Person<caret> {
  private final String name;
  private final int age;

  Person(int age, String name) {
    this.age = age;
    this.name = name;
  }

  Person(String name, int age) {
    this.name = name;
    this.age = age;
  }
}
