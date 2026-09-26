import org.jetbrains.annotations.Nullable;

interface Foo {
    void deadCode(@Nullable String s);
    void branch(@Nullable String s, boolean b);
    void tryStatement(@Nullable String s);
}

// Nullability inference bails out before reaching the dereference in every method below,
// so the explicit @Nullable on the super parameter is the only source of nullability
class Bar implements Foo {
    @Override
    public void deadCode(String s) {
        if (<warning descr="Condition is always false">false</warning>) {}
        System.out.println(s.<warning descr="Method invocation 'trim' may produce 'NullPointerException'">trim</warning>());
    }

    @Override
    public void branch(String s, boolean b) {
        if (b) {}
        System.out.println(s.<warning descr="Method invocation 'trim' may produce 'NullPointerException'">trim</warning>());
    }

    @Override
    public void tryStatement(String s) {
        try {
            System.out.println();
        } finally {
            System.out.println();
        }
        System.out.println(s.<warning descr="Method invocation 'trim' may produce 'NullPointerException'">trim</warning>());
    }
}
