import java.util.Collection;
import java.util.List;

interface A
{
    <S extends Cloneable & Comparable<?>> void foo(S x);
    <S extends Comparable<?> & Cloneable> void foo(S x);
}
