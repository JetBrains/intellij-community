// "Convert to record class" "true"
class Person<caret> {
  private final String name;
  private final int age;

  Person(String myName, int myAge) {
    this.name = myName;
    this.age = myAge;
  }

  Person(String myName, int myAge, String a) {
    this.name = myName;
    this.age = myAge;
  }
 }
