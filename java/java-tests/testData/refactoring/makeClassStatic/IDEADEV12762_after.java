class Test18 {

    String str;

    static class A {
        private final Test18 anObject;
        boolean flag;

        public A(Test18 anObject, boolean flag) {
            this.flag = flag;
            this.anObject = anObject;
        }

        void foo() {
            System.out.println("str = " + anObject.str);
        }
    }
}