import java.util.ArrayList;
import java.util.Collections;

class SortTest<R extends Comparable<R>> implements Comparable<SortTest<R>> {
    R r;

    public SortTest(R r) {
        this.r = r;
    }

    public int compareTo(SortTest<R> o) {
        return r.compareTo(o.r);
    }

    public static void main(String[] args) {
        ArrayList<SortTest<?>> list = new ArrayList<SortTest<?>>();
        SortTest<?> t1 = new SortTest<String>("");
        list.add(t1);
        SortTest<?> t2 = new SortTest<Integer>(0);
        list.add(t2);
        Collections.sort<error descr="'sort(java.util.List<T>)' in 'java.util.Collections' cannot be applied to '(java.util.ArrayList<SortTest<?>>)'">(list)</error>;
        t1.compareTo<error descr="'compareTo(SortTest<capture<?>>)' in 'SortTest' cannot be applied to '(SortTest<capture<?>>)'">(t2)</error>;

        //this should be OK
        SortTest<?>[] arr = new SortTest<?>[0];
        arr[0] = new SortTest<String>("");
    }

}
