// "Surround with 'if ((b ? foo(1) : foo(2)) != null)'" "true"
class A {
    void bar(String s) {}

    void foo(boolean b){
        if ((b ? foo(1) : foo(2)) != null) {
            bar(b ? foo(1) : foo(2));
        }
    }

    static String foo(int x) {
        return x > 0 ? "pos" : x < 0 ? "neg" : null;
    }
}