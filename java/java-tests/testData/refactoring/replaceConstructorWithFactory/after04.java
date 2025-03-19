class OuterClass {
    InnerClass createInnerClass(int _i) {
        return new InnerClass(_i);
    }

    class InnerClass {
        int i;
        private InnerClass(int _i) {
            i = _i;
        }
    }
    InnerClass myInner = createInnerClass(27);
    static int method() {
        OuterClass test = new OuterClass();
        InnerClass inner = test.createInnerClass(15);
    }
}