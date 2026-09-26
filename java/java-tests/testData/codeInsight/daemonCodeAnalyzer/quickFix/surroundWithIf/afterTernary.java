// "Introduce variable and surround with 'if (s != null)'" "true-preview"
class A {
    void bar(String s) {}

    void foo(boolean b){
        String s = b ? foo(1) : foo(2);
        if (s != null) {
            bar(s);
        }
    }

    static String foo(int x) {
        return x > 0 ? "pos" : x < 0 ? "neg" : null;
    }
}