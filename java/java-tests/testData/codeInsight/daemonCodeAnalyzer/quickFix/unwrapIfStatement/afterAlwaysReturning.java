// "Unwrap 'if' statement" "true-preview"
class X {
    boolean f(int[] a) {
        if (a.length == 0) return false;
        if (a.length == 1) {
            System.out.println();
            return true;
        }
        else {
            System.out.println("2");
            return true;
        }
    }
}