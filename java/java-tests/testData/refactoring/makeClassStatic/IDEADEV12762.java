class Test18 {

    String str;

    class <caret>A {
        boolean flag;

        public A(boolean flag) {
            this.flag = flag;
        }

        void foo() {
            System.out.println("str = " + str);
        }
    }
}