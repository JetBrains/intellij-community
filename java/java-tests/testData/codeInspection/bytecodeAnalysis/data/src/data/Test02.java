package bytecodeAnalysis.data;

import bytecodeAnalysis.*;

/**
 * @author lambdamix
 */
public final class Test02 {
  @ExpectContract(pure = true)
  @ExpectNotNull
  public String notNullString() {
    return "";
  }

  @ExpectContract(pure = true)
  @ExpectNotNull
  public String notNullStringDelegate() {
    return notNullString();
  }

  public boolean getFlagVolatile(@ExpectNotNull Test01 test01) {
    return test01.volatileFlag;
  }

  @ExpectContract(pure = true)
  public boolean getFlagPlain(@ExpectNotNull Test01 test01) {
    return test01.plainFlag;
  }
}