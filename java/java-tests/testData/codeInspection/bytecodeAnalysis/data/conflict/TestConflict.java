package bytecodeAnalysis.data;

// A class to test the clash of the same class in different source paths
// See also ../src/data/TestConflict.java
public class TestConflict {
  static native int throwInDataNativeInConflict();

  static int nativeInDataThrowInConflict() {
    throw new RuntimeException();
  }

  static int throwBoth() {
    throw new RuntimeException();
  }

  void pureInDataSideEffectInConflict() {
    System.out.println();
  }

  void sideEffectInDataPureInConflict() { }

  void pureBoth() { }

  void sideEffectBoth() {
    System.out.println();
  }
}