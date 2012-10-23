import java.util.Collection;
import java.util.List;

interface A
{
    <<error descr="'addAll(Collection<? extends E>)' in 'java.util.Collection' clashes with 'addAll(Collection<? extends E>)' in 'java.util.List'; both methods have same erasure, yet neither overrides the other"></error>T extends List<?> & Collection<? extends Cloneable>> void foo(T x);
}