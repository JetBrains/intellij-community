import java.lang.Override;

class PrivateConstructor {
  private final int myA;

  private PrivateConstructor(int a) {
    myA = a;
  }

  @Override
  public int hashCode() {
    return myA;
  }
}