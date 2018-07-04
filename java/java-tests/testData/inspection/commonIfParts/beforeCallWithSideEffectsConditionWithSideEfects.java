// "Extract common part with variables from if (may change semantics)" "INFORMATION"

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

  boolean conditionWithPossibleSideEffects() {

  }

  public void main(String[] args) {
    if<caret>(conditionWithPossibleSideEffects()) {
      Person person = new Person(12, "aaa");
      renamePerson(person, "ccc");
      int x = 12;
      work(x);
    } else {
      Person person = new Person(12, "aaa");
      renamePerson(person, "ccc");
      int x = 1;
      work(x);
    }
  }
}