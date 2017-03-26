// "Add 'C2' as 2nd parameter to method 'f'" "true"

final class X {

    public static final class C2 { }

    public static final class C {

        public static void main(String[] argArr) {
            final C2 c2 = new C2();
            final C c = f(123, <caret>c2);
        }

        public static C f(final int i) {
            return new C(i, c2);
        }

        private final int i;
        private final C2 c2;

        private C(final int i, C2 c2) {
            this.i = i;
            this.c2 = c2;
        }
    }
}