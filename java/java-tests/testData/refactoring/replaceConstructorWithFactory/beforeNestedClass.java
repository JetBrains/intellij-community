class OuterClass {
    static InnerClass createInnerClass(int _i) {
      return new InnerClass(_i);
    }
  
    static class InnerClass {
        int i;
        InnerClass<caret>(int _i) {
            i = _i;
        }
    }
    InnerClass myInner = new InnerClass(27);
    static int method() {
        InnerClass inner = new InnerClass(15);
    }
}