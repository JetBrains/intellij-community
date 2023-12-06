class OuterClass {
    static class InnerClass {
        int i;
        private InnerClass(int _i) {
            i = _i;
        }

        static InnerClass createInnerClass(int _i) {
            return new InnerClass(_i);
        }
    }
    InnerClass myInner = InnerClass.createInnerClass(27);
    static int method() {
        InnerClass inner = InnerClass.createInnerClass(15);
    }
}