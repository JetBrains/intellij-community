// "Change 'new TTT[0][]' to 'new TTT[]'" "true-preview"
public class TTT {
    void f() {
        String s = new Object();
        int[][] i = new Object();
        TTT[] f = <caret>new TTT[0][];

    }
}
