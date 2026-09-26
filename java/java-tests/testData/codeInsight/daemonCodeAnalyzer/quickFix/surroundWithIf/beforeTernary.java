// "Introduce variable and surround with 'if (s != null)'" "true-preview"
class A {
    void bar(String s) {}

    void foo(boolean b){
        bar(b ? foo(1)<caret> : foo(2));
    }

    static String foo(int x) {
        return x > 0 ? "pos" : x < 0 ? "neg" : null;
    }
}