import java.util.Collection;
import java.util.List;

interface A
{
    <S extends Collection<?> & List<?>> void foo(S x);
}

<error descr="Class 'B' must either be declared abstract or implement abstract method 'foo(S)' in 'A'">class B  implements A</error>
{
    <error descr="'foo(Collection<?>)' in 'B' clashes with 'foo(S)' in 'A'; both methods have same erasure, yet neither overrides the other">public void foo(Collection<?> x)</error> { }
    public <S extends List<?> & Collection<?>> void foo(S x) { }
}