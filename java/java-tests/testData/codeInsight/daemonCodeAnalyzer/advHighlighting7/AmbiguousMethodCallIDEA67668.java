import java.util.Collection;
import java.util.List;

interface A
{
    <S extends Collection<?> & List<?>> void foo(S x);
    <S extends List<?>> void foo(S x);
}


class B
{
    public static void main(String[] args) {
        A a = null;
        a.foo<error descr="Ambiguous method call: both 'A.foo(List<?>)' and 'A.foo(List<?>)' match">(null)</error>;
    }
}