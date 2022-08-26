// "Remove redundant 'else'" "true-preview"
class T {
    static void foo(boolean something) {
        if (something) {
            return;
        } // a
        System.out.println(); // b
        // c
    }
}