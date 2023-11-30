class OuterClass {
    static InnerClass createInnerClass(int _i) {
      return newInnerClass(_i);
    }

    static InnerClass newInnerClass(int _i) {
        return new InnerClass(_i);
    }

    static class InnerClass {
        int i;
        private InnerClass(int _i) {
            i = _i;
        }
    }
    InnerClass myInner = newInnerClass(27);
    static int method() {
        InnerClass inner = newInnerClass(15);
    }
}