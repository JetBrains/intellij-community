// "Change 'new TTT[0][]' to 'new TTT[]'" "true"
public class TTT {
    void f() {
        String s = new Object();
        int[][] i = new Object();
        TTT[] f = new TTT[<caret><selection>0</selection>];

    }
}
