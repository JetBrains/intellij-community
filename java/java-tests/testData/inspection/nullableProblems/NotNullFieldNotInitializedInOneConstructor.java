import org.jetbrains.annotations.*;

class Test {
  <warning descr="Not-null fields must be initialized">@NotNull</warning> Object member;

  public Test() {
  }

  public Test(@NotNull Object member) {
    this.member = member;
  }

  private void accessMember() {
    member = new Object();
  }
}