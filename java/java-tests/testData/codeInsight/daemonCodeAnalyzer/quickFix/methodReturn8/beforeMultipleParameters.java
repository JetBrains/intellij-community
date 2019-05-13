// "Make 'foo' return 'java.util.List<java.lang.Integer>'" "true"
import java.util.List;
interface Main<A, B> {
  List<A> foo();
}

class MainImpl implements Main<String, String> {
  @Override
  public List<String> foo() {
    return b<caret>ar();
  }

  private List<Integer> bar() {
    return null;
  }
}
