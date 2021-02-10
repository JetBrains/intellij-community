import typeUse.*;

public class IncorrectPlacement {
  void test(<warning descr="Receiver parameter is inherently not-null">@Nullable</warning> IncorrectPlacement this) {
    <warning descr="Outer type is inherently not-null">@Nullable</warning> IncorrectPlacement.Inner a1;
    IncorrectPlacement.@Nullable Inner a2;
  }
  
  class Inner {}
}