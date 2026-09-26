import org.jetbrains.annotations.Nullable;

interface Foo {
    void test(@Nullable String s);
}

class Bar implements Foo {
    @Override
    public void test(String s) {
        System.out.println(s.<warning descr="Method invocation 'trim' may produce 'NullPointerException'">trim</warning>());
    }
}