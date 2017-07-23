// "Unwrap 'if' statement" "true"
class X {
    boolean f(int[] a) {
        if (a.length == 0) return false;
        if (a.length == 1) {
            System.out.println();
            return true;
        }
        else if (a.<caret>length > 1) {
            System.out.println("2");
            return true;
        }
        return false;
    }
}