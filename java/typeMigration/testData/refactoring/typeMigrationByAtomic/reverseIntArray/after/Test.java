import java.util.concurrent.atomic.AtomicIntegerArray;

class Test {
    int[] a = new int[1];


    void foo() {
        a[0]++;
        System.out.println(++a[0]);
        a[0]--;
        if (--a[0] == 0) {
            a[0] += 2;
            a[0] = a[0] * 2;
            if (a[0] == 0) {
                System.out.println(a[0] + 7);
            }
        }
    }
}