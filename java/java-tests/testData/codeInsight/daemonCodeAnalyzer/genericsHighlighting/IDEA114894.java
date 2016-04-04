import java.util.Comparator;
import java.util.List;

class IdeaBugTest {
    public void foo(List<Base> base) {
        MyCollection.fun(base, <error descr="'fun(java.util.List<? extends Base>, java.util.Comparator<? super Base>)' in 'MyCollection' cannot be applied to '(java.util.List<Base>, SubComparator)'">new SubComparator()</error>);
    }
}

class Base { }
class Sub extends Base { }

class SubComparator implements Comparator<Sub> {
    public int compare(Sub o1, Sub o2) {
        return 0;
    }
}

class MyCollection<E> {
    public static <T> void fun(List<? extends T> list, Comparator<? super T> comp) {
    }
}