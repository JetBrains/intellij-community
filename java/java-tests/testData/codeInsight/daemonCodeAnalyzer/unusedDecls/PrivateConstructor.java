import java.lang.Override;

class <warning descr="Class 'PrivateConstructor' is never used">PrivateConstructor</warning> {
  private final int myA;

  private PrivateConstructor(int a) {
    myA = a;
  }

  @Override
  public int hashCode() {
    return myA;
  }
}