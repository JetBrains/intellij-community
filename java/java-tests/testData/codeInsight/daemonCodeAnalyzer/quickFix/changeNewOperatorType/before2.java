// "Change 'new Object()' to 'new int[][]'" "true"
public class TTT {
    void f() {
        String s = new Object();
        int[][] i = new Object();<caret>
        int[] f = new int[0][];

    }
}
