package bytecodeAnalysis.data;

import bytecodeAnalysis.ExpectNotNull;
import bytecodeAnalysis.ExpectContract;

public class TestField {
  @ExpectNotNull
  public static final Object O1 = new Object();
  @ExpectNotNull
  public static final Object O2 = getObject();
  @ExpectNotNull
  public static final Runnable O3 = () -> {};

  public static Object O4 = new Object(); // non-final
  public final Object O5 = new Object(); // non-static

  @ExpectNotNull
  @ExpectContract(value="->new",pure=true)
  private static Object getObject() {
    return new Object();
  }
}