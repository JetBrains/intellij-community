// "Replace with sum()" "true"

import java.util.List;

public class Main {
  interface Person {
    int getAge();
  }

  public long test(List<Person> collection, Person include) {
    long i = 0;
    for(Person person : collec<caret>tion) {
      if(!include.equals(person))
        continue;
      i = i + person.getAge();
    }
    return i;
  }
}