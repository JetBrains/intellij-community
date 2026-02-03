import java.util.List;
class C {
    void foo(int[] a, List<Integer> b) {
        int i = 1;
        int n = <selection>bar(a[i], b.get(i))</selection>;
        System.out.println(n);
    }

    int bar(int a, int b) {
        return a + b;
    }
}