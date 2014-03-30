import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class P2 {
    <warning descr="Overridden methods are not annotated">@NotNull</warning>
    String foo(<warning descr="Overridden method parameters are not annotated">@NotNull</warning> <error descr="Cannot resolve symbol 'P'">P</error> p) {
        return "";
    }
}

class PPP extends P2 {
    String <warning descr="Not annotated method overrides method annotated with @NotNull">foo</warning>(<error descr="Cannot resolve symbol 'P'">P</error> <warning descr="Not annotated parameter overrides @NotNull parameter">p</warning>) {
        return super.foo(p);
    }
}
class PPP2 extends P2 {

    String <warning descr="Not annotated method overrides method annotated with @NotNull">foo</warning>(<error descr="Cannot resolve symbol 'P'">P</error> <warning descr="Not annotated parameter overrides @NotNull parameter">p</warning>) {
        return super.foo(p);
    }
}

///////  in library
interface Foo {
    @NotNull
    String getTitle();
}
<error descr="Unhandled exception: java.awt.HeadlessException">class FooImpl extends java.awt.Frame implements Foo</error> {
//    public String getTitle() {
//        return super.getTitle();    //To change body of overridden methods use File | Settings | File Templates.
//    }
}


interface I1 {
  @Nullable
  Object foo();
}

interface I2 extends I1 {
  @NotNull
  Object foo();
}

class A implements I1 {
  @Override
  public Object foo() {
    // returns something
  <error descr="Missing return statement">}</error>
}

class B extends A implements I2 {
}