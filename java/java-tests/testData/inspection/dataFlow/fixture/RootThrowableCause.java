import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class Doo {

  void foo(Throwable e) {
    Throwable t = e;

    while (t.getCause() != null) t = t.getCause();
    if (e != t) {
      System.out.println();
    }
  }

}

abstract class Test04 {
  @Nullable
  @Contract(pure = true)
  abstract Test04 getParent();

  Test04 getTopParent() {
    Test04 top = this;
    while (top.getParent() != null) {
      top = top.getParent();
    }
    return top;
  }
}