class GenericsTest {

    static class SomeClass<U> {
        public <T> T getX() {
            return null;
        }
        public String f() {
            return this.<String>getX();
        }
    }



    public static void main(String[] args) {

        String v1 = new SomeClass().<error descr="Type arguments given on a raw method"><String></error>getX();
        String v2 = new SomeClass().f();  //

    }

}
