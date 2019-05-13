import java.util.Collection;
import java.util.List;

interface A
{
    <S extends Collection<?> & List<?>> void foo(S x);
    <S extends List> String foo(S x);
}


class B
{
    public static void main(String[] args) {
        A a = null;
        char c = a.foo(null).<error descr="Cannot resolve method 'charAt(int)'">charAt</error>(0);
    }
}