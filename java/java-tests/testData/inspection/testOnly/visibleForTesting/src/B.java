public class B {
  void publicMethod() {
    new A().invisibleMethod(2);
    new A().visibleMethod(2);
    new A().relaxedToPackageLevel(2);
  }
}