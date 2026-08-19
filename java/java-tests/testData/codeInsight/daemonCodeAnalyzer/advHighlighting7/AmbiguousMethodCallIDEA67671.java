import java.util.Collection;
import java.util.List;

interface A
{
    <S extends Collection<?> & List<?>> void foo(S x);
}

// 'foo(S)' below overrides 'A.foo(S)': the order of the bounds of a type parameter never affected overriding
class B  implements A
{
    <error descr="'foo(Collection<?>)' in 'B' clashes with 'foo(S)' in 'A'; both methods have same erasure, yet neither overrides the other">public void foo(Collection<?> x)</error> { }
    public <S extends List<?> & Collection<?>> void foo(S x) { }
}