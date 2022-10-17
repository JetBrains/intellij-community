// "Create record 'MyRecord'" "true-preview"
class Test {
  void foo () {
    new MyRecord<String> (1, 2);
  }
}

public record MyRecord<T>(int i, int i1) {
}