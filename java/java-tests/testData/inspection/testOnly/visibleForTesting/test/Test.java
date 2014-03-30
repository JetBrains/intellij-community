public class Test {
  void publicMethod() {
    new A().invisibleMethod(3);
    new A().visibleMethod(3);
    new A().relaxedToPackageLevel(3);
  }
}