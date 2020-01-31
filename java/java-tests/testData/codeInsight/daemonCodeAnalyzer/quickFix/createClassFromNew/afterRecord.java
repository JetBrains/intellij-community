// "Create record 'MyRecord'" "true"
class Test {
  void foo () {
    new MyRecord (1, 2);
  }
}

public record MyRecord(int i, int i1) {
}