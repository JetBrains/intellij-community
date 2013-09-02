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

  }
  
  @com.google.common.annotations.VisibleForTesting
  void unknownRealVisibilityMethod(int a) {

  }
}