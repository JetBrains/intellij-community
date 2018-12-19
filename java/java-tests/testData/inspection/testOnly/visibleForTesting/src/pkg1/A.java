package pkg1;
public class A {
  void publicMethod() {
    invisibleMethod(1);
    visibleMethod(1);
  }

  @com.android.annotations.VisibleForTesting
  void invisibleMethod(int a) {

  }

  @com.android.annotations.VisibleForTesting(visibility=com.android.annotations.VisibleForTesting.Visibility.PROTECTED)
  void visibleMethod(int a) {
    relaxedToPackageLevel(a);
  }
  
  @com.google.common.annotations.VisibleForTesting
  void relaxedToPackageLevel(int a) {

  }

  protected void aProtectedMethod() {}

  @com.google.common.annotations.VisibleForTesting
  static class FooException extends RuntimeException {
    FooException(String message) {
      super(message);
    }
  }

  public static void usingExceptionPrivately(String[] args) {
    A.FooException exception =
      new A.FooException("");
  }
}