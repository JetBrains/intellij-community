// "Remove Qualifier" "true"
class E {
    class Outer {
    class S {
        public static final int SS = 0;
    }
    }

    Outer f() {
        int s = <caret>Outer.S.SS;
        // int s1 = <error descr="Expected class or package">this</error>.Outer.S.SS;
        int s2 = Outer.S.SS;
        return null;
    }
}
