class RecordWithCompactConstructor {
  void test() {
    System.out.println(new <caret>Person("John", 20).name());
  }

  record Person(String name, int age) {
    Person(String name, int age) {
      this.name = name;
      this.age = age;
    }
  }
}