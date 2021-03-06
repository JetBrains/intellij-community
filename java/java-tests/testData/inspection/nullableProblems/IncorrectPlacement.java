import typeUse.*;

public class IncorrectPlacement extends <warning descr="Nullability annotation is not applicable to extends/implements lists">@Nullable</warning> Object
        implements <warning descr="Nullability annotation is not applicable to extends/implements lists">@Nullable</warning> Cloneable {
  
  <warning descr="Nullability annotation is not applicable to constructors">@Nullable</warning> IncorrectPlacement() {}
  
  void test(<warning descr="Receiver parameter is inherently not-null">@Nullable</warning> IncorrectPlacement this) {
    <warning descr="Outer type is inherently not-null">@Nullable</warning> IncorrectPlacement.Inner a1;
    IncorrectPlacement.@Nullable Inner a2;
  }
  
  <warning descr="Primitive type members cannot be annotated">@NotNull</warning> int[] data;
  
  class Inner {}
}