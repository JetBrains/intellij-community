// "Make 'foo' return 'java.util.List<java.lang.Integer>'" "true"
import java.util.List;
interface Main<A, B> {
  List<A> foo();
}

class MainImpl implements Main<Integer, String> {
  @Override
  public List<Integer> foo() {
    return bar();
  }

  private List<Integer> bar() {
    return null;
  }
}
