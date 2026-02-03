import java.util.Comparator;
import java.util.Set;

class CastError {
    public void foo(Comparator<? super byte[]> comparator) throws Exception {
        MyComparator comparator1 = (MyComparator) comparator;
    }

    public void foo1(Comparator<byte[]> comparator) throws Exception {
        MyComparator comparator1 = (MyComparator) comparator;
    }

    public void foo2(Comparator<? extends byte[]> comparator) throws Exception {
        MyComparator comparator1 = (MyComparator) comparator;
    }

    public void foo3(Comparator<? super String[]> comparator) throws Exception {
        MyComparator comparator1 = <error descr="Inconvertible types; cannot cast 'java.util.Comparator<capture<? super java.lang.String[]>>' to 'MyComparator'">(MyComparator) comparator</error>;
    }

    public void foo4(Comparator<? extends String[]> comparator) throws Exception {
        MyComparator comparator1 = <error descr="Inconvertible types; cannot cast 'java.util.Comparator<capture<? extends java.lang.String[]>>' to 'MyComparator'">(MyComparator) comparator</error>;
    }

    public void foo5(Comparator<?> comparator) throws Exception {
        MyComparator comparator1 = (MyComparator) comparator;
    }

    //--||--||--||--||--||--||--||--||--||--||--||--||--||--||--||--||--||--||--

    public void sfoo(Set<Comparator<? super byte[]>> comparator) throws Exception {
        Set<MyComparator> comparator1 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Comparator<? super byte[]>>' to 'java.util.Set<MyComparator>'">(Set<MyComparator>) comparator</error>;
    }

    public void sfoo1(Set<Comparator<byte[]>> comparator) throws Exception {
        Set<MyComparator> comparator1 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Comparator<byte[]>>' to 'java.util.Set<MyComparator>'">(Set<MyComparator>) comparator</error>;
    }

    public void sfoo2(Set<Comparator<? extends byte[]>> comparator) throws Exception {
        Set<MyComparator> comparator1 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Comparator<? extends byte[]>>' to 'java.util.Set<MyComparator>'">(Set<MyComparator>) comparator</error>;
    }

    public void sfoo3(Set<Comparator<? super String[]>> comparator) throws Exception {
        Set<MyComparator> comparator1 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Comparator<? super java.lang.String[]>>' to 'java.util.Set<MyComparator>'">(Set<MyComparator>) comparator</error>;
    }

    public void sfoo4(Set<Comparator<? extends String[]>> comparator) throws Exception {
        Set<MyComparator> comparator1 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Comparator<? extends java.lang.String[]>>' to 'java.util.Set<MyComparator>'">(Set<MyComparator>) comparator</error>;
    }

    public void sfoo5(Set<Comparator<?>> comparator) throws Exception {
        Set<MyComparator> comparator1 = <error descr="Inconvertible types; cannot cast 'java.util.Set<java.util.Comparator<?>>' to 'java.util.Set<MyComparator>'">(Set<MyComparator>) comparator</error>;
    }
}

abstract class MyComparator implements Comparator<byte[]> {
}