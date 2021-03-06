abstract sealed class Super permits Test {
  abstract void doSmth();
}

final class Test extends Super {
  @Override
  void doSmth() {
  }
}