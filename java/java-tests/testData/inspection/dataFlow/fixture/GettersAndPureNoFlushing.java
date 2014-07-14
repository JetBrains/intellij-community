import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class Doo {

  @Nullable
  Object getMethod() {return null;}

  boolean isSomething() { return false;}

  @Contract(pure=true)
  boolean pureSomething() { return false;}

  public void main2() {
    if (getMethod() == null && !isSomething()) {
      return;
    } else {
      System.out.println(<warning descr="Method invocation 'getMethod().hashCode()' may produce 'java.lang.NullPointerException'">getMethod().hashCode()</warning>);
    }
  }

  public void main3() {
    if (getMethod() == null && !pureSomething()) {
      return;
    } else {
      System.out.println(<warning descr="Method invocation 'getMethod().hashCode()' may produce 'java.lang.NullPointerException'">getMethod().hashCode()</warning>);
    }
  }

}

