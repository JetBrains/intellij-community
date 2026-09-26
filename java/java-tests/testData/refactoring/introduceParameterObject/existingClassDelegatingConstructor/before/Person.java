class Person {
  private final String firstName;
  private final String lastName;
  private final int age;
  
  Person(String firstName, String lastName) {
    this(firstName, lastName, 1);
  }

  Person(String firstName, String lastName, int age) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.age = age;
  }
}