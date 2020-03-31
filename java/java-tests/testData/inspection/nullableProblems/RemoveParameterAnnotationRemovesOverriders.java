import org.jetbrains.annotations.*;

interface Foo {
  void getTime(<warning descr="Primitive type members cannot be annotated">@Nul<caret>lable</warning> int a);
}

class Bar implements Foo {
  public void getTime(<warning descr="Primitive type members cannot be annotated">@Nullable</warning> int a) {}
}