import java.util.List;

class MyTest {

  void m(List<Person> l) {
    l.stream().map(Person::getName)
  }

}

interface Person {
  String getName();
}