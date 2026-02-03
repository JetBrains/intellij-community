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

        String v1 = new SomeClass().<String><error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.String'">getX</error>();
        String v2 = new SomeClass().f();  //

    }

}
