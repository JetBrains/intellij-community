import typeUse.*;

public class IncorrectPlacementAmbiguous extends <warning descr="Nullability annotation is not applicable to extends/implements lists">@Nullable</warning> Object
        implements <warning descr="Nullability annotation is not applicable to extends/implements lists">@Nullable</warning> Cloneable {
  
  <warning descr="Nullability annotation is not applicable to constructors">@Nullable</warning> IncorrectPlacementAmbiguous() {}
  
  void test(<warning descr="Receiver parameter is inherently not-null">@Nullable</warning> IncorrectPlacementAmbiguous this) {
    @Nullable IncorrectPlacementAmbiguous.Inner a1;
    IncorrectPlacementAmbiguous.@Nullable Inner a2;
  }

  <warning descr="Primitive type members cannot be annotated">@NotNull</warning> int[] data;
  
  class Inner {}
}