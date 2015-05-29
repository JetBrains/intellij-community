import java.util.List;

class MyTest {

  void m(List<Person> l) {
    l.stream().map(<caret>)
  }

}

interface Person {
  String getName();
}