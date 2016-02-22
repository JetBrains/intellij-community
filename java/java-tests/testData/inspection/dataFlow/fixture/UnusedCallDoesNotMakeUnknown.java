import org.jetbrains.annotations.Nullable;

class Doo {

  @Nullable
  Object getMethod() {
    return null;
  }

  public void main(String[] args) {
    Object method = getMethod();
    System.out.println(getMethod().<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>());
  }
}