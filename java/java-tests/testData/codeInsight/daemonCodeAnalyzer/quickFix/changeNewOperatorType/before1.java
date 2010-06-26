// "Change 'new Object()' to 'new String()'" "true"
public class TTT {
    void f() {
        <caret>String s = new Object();
        int[][] i = new Object();
        int[] f = new int[0][];

    }
}
