import java.util.Collection;
import java.util.List;

interface A
{
    <<error descr="'java.util.Collection' cannot be inherited with different type arguments: 'capture<?>' and 'capture<? extends java.lang.Cloneable>'"></error>T extends List<?> & Collection<? extends Cloneable>> void foo(T x);
}
