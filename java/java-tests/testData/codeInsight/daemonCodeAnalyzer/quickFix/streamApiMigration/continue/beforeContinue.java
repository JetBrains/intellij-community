// "Replace with sum()" "true"

import java.util.List;

public class Main {
  interface Person {
    int getAge();
  }

  public long test(List<Person> collection) {
    long i = 0;
    for(Person person : col<caret>lection) {
      if(person == null) {
        continue;
      }
      i = i + person.getAge();
    }
    return i;
  }
}