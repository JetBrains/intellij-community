package bytecodeAnalysis.data;

import bytecodeAnalysis.ExpectContract;

// A class to test the clash of the same class in different source paths
// See also ../../conflict/TestConflict.java
public class TestConflict {
  static int throwInDataNativeInConflict() {
    throw new RuntimeException();
  }

  static native int nativeInDataThrowInConflict();

  @ExpectContract(value = "->fail", pure = true)
  static int throwBoth() {
    throw new RuntimeException();
  }

  void pureInDataSideEffectInConflict() { }

  void sideEffectInDataPureInConflict() {
    System.out.println();
  }

  @ExpectContract(pure = true)
  void pureBoth() { }

  void sideEffectBoth() {
    System.out.println();
  }
}