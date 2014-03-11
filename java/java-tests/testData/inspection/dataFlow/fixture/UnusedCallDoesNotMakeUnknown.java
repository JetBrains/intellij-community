import org.jetbrains.annotations.Nullable;

class Doo {

  @Nullable
  Object getMethod() {
    return null;
  }

  public void main(String[] args) {
    Object method = getMethod();
    System.out.println(<warning descr="Method invocation 'getMethod().hashCode()' may produce 'java.lang.NullPointerException'">getMethod().hashCode()</warning>);
  }
}