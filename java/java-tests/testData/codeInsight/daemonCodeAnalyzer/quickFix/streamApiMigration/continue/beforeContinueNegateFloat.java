// "Replace with sum()" "true"

import java.util.List;

public class Main {
  interface Person {
    double getAge();
  }

  public double test(List<Person> collection) {
    double d = 0;
    for(Person person : collec<caret>tion) {
      if(person.getAge() == 10)
        continue;
      d = d + person.getAge();
    }
    return d;
  }
}