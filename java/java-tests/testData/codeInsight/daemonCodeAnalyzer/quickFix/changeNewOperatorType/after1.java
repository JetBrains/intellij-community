// "Change 'new Object()' to 'new String()'" "true-preview"
public class TTT {
    void f() {
        String s = new String(<caret>);
        int[][] i = new Object();
        int[] f = new int[0][];

    }
}
