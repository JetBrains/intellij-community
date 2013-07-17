public class A {
  void publicMethod() {
    invisibleMethod(1);
    visibleMethod(1);
  }

  @com.google.common.annotations.VisibleForTesting()
  void invisibleMethod(int a) {

  }

  @com.google.common.annotations.VisibleForTesting(visibility=com.google.common.annotations.VisibleForTesting.Visibility.PROTECTED)
  void visibleMethod(int a) {

  }
}