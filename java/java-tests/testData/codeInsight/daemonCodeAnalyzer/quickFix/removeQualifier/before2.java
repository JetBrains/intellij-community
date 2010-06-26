// "Remove Qualifier" "true"
class E {
    class Outer {
        public static final int SS = 0;
    class S {
        public static final int SS = 0;
    }
    }

    Outer f() {
        int s = this<caret>.Outer.SS;
        return null;
    }
}
