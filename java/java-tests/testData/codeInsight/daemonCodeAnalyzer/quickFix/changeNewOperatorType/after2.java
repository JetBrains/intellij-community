// "Change 'new Object()' to 'new int[][]'" "true-preview"
public class TTT {
    void f() {
        String s = new Object();
        int[][] i = new int[<caret><selection>0</selection>][];
        int[] f = new int[0][];

    }
}
