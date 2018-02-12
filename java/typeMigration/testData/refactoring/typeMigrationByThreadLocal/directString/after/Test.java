class Test {
    ThreadLocal<String> myS = new ThreadLocal<String>() {
        @Override
        protected String initialValue() {
            return "";
        }
    };

    void foo() {
        if (myS.get() == null) {
            System.out.println(myS.get());
        }
    }
}