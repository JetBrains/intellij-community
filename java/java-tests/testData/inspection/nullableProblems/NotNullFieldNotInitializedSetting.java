import org.jetbrains.annotations.*;

class Test {
  @NotNull Object member;

  private void accessMember() {
    member = new Object();
  }
}