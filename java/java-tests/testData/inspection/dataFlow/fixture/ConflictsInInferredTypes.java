import foo.NotNull;
import foo.Nullable;

abstract class Bar<T> {
    abstract @NotNull T getNN(T t);
    abstract @Nullable T getNullable(T t);

    void m(Bar<@Nullable String> bNullable,
           Bar<@NotNull String>  bNotNull,
           String str) {
        final int length =  bNullable.getNN(str).length();
        final int length1 = bNullable.getNullable(str).<warning descr="Method invocation 'length' may produce 'java.lang.NullPointerException'">length</warning>();

        final int length2 = bNotNull.getNN(str).length();
        final int length3 = bNotNull.getNullable(str).<warning descr="Method invocation 'length' may produce 'java.lang.NullPointerException'">length</warning>();
    }
}
