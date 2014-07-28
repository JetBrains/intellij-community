/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Nov 15, 2004
 * Time: 5:40:02 PM
 * To change this template use File | Settings | File Templates.
 */
public class Test {
    Integer sum(int i, int j) {
        return i + j;
    }

    int[] foo(int n, int k) {
        int[] a = new int[n];

        for (int i = 0; i < a.length; i++) {
            a[i] = sum(i, k);
        }

        return a;
    }
}
