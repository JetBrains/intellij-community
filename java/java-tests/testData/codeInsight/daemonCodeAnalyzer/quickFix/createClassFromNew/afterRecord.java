// "Create record 'MyRecord'" "true-preview"
class Test {
  void foo () {
    new MyRecord (1);
  }
}

public record MyRecord(int i) {
}