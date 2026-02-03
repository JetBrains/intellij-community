// "Join declaration and assignment" "false"
class C {
    void foo(int[] a) {
        for (int n = 0; ; <caret>n+=1) {
            if (n >= a.length) break;
        }
    }
}