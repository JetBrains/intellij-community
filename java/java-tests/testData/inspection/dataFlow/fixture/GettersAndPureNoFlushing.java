import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class Doo {

  @Nullable
  Object getMethod() {return null;}

  @Nullable
  @Contract(pure=true)
  Object getMethodPure() {return null;}

  boolean isSomething() { return false;}

  @Contract(pure=true)
  boolean pureSomething() { return false;}

  public void main2() {
    // isSomething is non-pure: flush
    if (getMethod() == null && !isSomething()) {
      return;
    } else {
      System.out.println(getMethod().hashCode());
    }
  }

  public void main3() {
    if (getMethod() == null && !pureSomething()) {
      return;
    } else {
      // still not sure about nullability as getMethod() is not pure
      System.out.println(getMethod().hashCode());
    }
  }

  public void main4() {
    if (getMethodPure() == null && !pureSomething()) {
      return;
    } else {
      System.out.println(getMethodPure().<warning descr="Method invocation 'hashCode' may produce 'NullPointerException'">hashCode</warning>());
    }
  }

}

