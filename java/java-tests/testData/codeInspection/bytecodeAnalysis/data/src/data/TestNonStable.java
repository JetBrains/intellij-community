package bytecodeAnalysis.data;

import bytecodeAnalysis.*;

public class TestNonStable {
  public String asString1() {
    return asString();
  }

  @ExpectContract(pure = true)
  @ExpectNotNull
  public String asString() {
    return "";
  }
}