import typeUse.*;

public class IncorrectPlacementAmbiguousJava6 extends <error descr="Type annotations are not supported at language level '6'"><warning descr="Nullability annotation is not applicable to extends/implements clause">@Nullable</warning></error> Object
        implements <error descr="Type annotations are not supported at language level '6'"><warning descr="Nullability annotation is not applicable to extends/implements clause">@Nullable</warning></error> Cloneable {
  
  <warning descr="Nullability annotation is not applicable to constructors">@Nullable</warning> IncorrectPlacementAmbiguousJava6() {}
  
  void test(<error descr="Receiver parameters are not supported at language level '6'"><warning descr="Receiver parameter is inherently non-null">@Nullable</warning> IncorrectPlacementAmbiguousJava6 this</error>) {
    @Nullable IncorrectPlacementAmbiguousJava6.Inner a1;
    IncorrectPlacementAmbiguousJava6.<error descr="Type annotations are not supported at language level '6'">@Nullable</error> Inner a2;
  }

  @NotNull int[] data;
  
  class Inner {}
}