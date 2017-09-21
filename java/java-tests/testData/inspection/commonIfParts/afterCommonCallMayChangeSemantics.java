// "Extract common part with variables from if (may change semantics)" "true"

import java.util.List;
import java.util.Map;

public class Main {
  class Person {
    int age;
    String name;

    public Person(int age, String name) {
      this.age = age;
      this.name = name;
    }
  }

  void renamePerson(Person person, String newName) {
    person.name = newName;
  }

  void work(int i) {

  }

  public void main(String[] args) {
      Person person;
      renamePerson(person, "ccc");
      int x;
      if(true) {
          person = new Person(12, "aaa");
          x = 12;
      } else {
          person = new Person(12, "aaa");
          x = 1;
      }
      work(x)
  }
}