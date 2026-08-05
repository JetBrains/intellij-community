import java.util.Collection;
import java.util.List;

interface A
{
    <error descr="'foo(S)' is already defined in 'A'"><S extends Cloneable & Comparable<?>> void foo(S x);</error>
    <error descr="'foo(S)' is already defined in 'A'"><S extends Comparable<?> & Cloneable> void foo(S x);</error>
}
