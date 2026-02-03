import org.jetbrains.annotations.*;

class Test {
  <warning descr="Not-null fields must be initialized">@NotNull</warning> Object member;

  private void accessMember() {
    member = new Object();
  }
}