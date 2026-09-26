import org.jetbrains.annotations.Nullable;
class Foo {
    @Nullable Foo foo() {
        return null;
    }

    public void bar() {
        if (foo() != null &&
          foo().<warning descr="Method invocation 'foo' may produce 'NullPointerException'">foo</warning>() != null &&
          foo().<warning descr="Method invocation 'foo' may produce 'NullPointerException'">foo</warning>().<warning descr="Method invocation 'foo' may produce 'NullPointerException'">foo</warning>() != null) {

        }
    }
}
