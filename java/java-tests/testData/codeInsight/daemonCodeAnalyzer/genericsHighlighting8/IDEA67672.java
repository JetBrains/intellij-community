import java.util.Collection;
import java.util.List;

interface A
{
    <<error descr="'add(E)' in 'java.util.List' clashes with 'add(E)' in 'java.util.Collection'; both methods have same erasure, yet neither overrides the other"></error><error descr="'add(E)' in 'java.util.List' clashes with 'add(E)' in 'java.util.Collection'; both methods have same erasure, yet neither overrides the other"></error>T extends List<?> & Collection<? extends Cloneable>> void foo(T x);
}
