import typeUse.*;

public class IncorrectPlacement extends <warning descr="Nullability annotation is not applicable to extends/implements clause">@Nullable</warning> Object
        implements <warning descr="Nullability annotation is not applicable to extends/implements clause">@Nullable</warning> Cloneable {
  
  <warning descr="Nullability annotation is not applicable to constructors">@Nullable</warning> IncorrectPlacement() {}
  
  void test(<warning descr="Receiver parameter is inherently non-null">@Nullable</warning> IncorrectPlacement this) {
    <warning descr="Outer type is inherently non-null">@Nullable</warning> IncorrectPlacement.Inner a1;
    IncorrectPlacement.@Nullable Inner a2;
  }
  
  void fqn() {
    <warning descr="Annotation on fully-qualified name must be placed before the last component">@Nullable</warning> java.lang.String[] strs;
  }
  
  <warning descr="Primitive type members cannot be annotated">@NotNull</warning> int[] data;
  
  class Inner {}
}