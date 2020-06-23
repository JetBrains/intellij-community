import java.util.List;
class C {
    void foo(int[] a, List<Integer> b) {
        int i = 1;
        int n = newMethod(a[i], b.get(i));
        System.out.println(n);
    }

    private int newMethod(int a, Integer b) {
        return bar(a, b);
    }

    int bar(int a, int b) {
        return a + b;
    }
}