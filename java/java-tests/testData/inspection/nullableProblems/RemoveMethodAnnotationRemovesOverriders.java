import org.jetbrains.annotations.*;

interface Foo {
  <warning descr="Primitive type members cannot be annotated">@Nul<caret>lable</warning>
  long getTime();
}

class Bar implements Foo {
  <warning descr="Primitive type members cannot be annotated">@Nullable</warning>
  @Override
  public long getTime() {
    return 0;
  }
}