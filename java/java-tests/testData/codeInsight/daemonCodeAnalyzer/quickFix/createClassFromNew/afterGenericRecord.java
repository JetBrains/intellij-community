// "Create record 'MyRecord'" "true"
class Test {
  void foo () {
    new MyRecord<String> (1, 2);
  }
}

public record MyRecord<T>(int i, int i1) {
}