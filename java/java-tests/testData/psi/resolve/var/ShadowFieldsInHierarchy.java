class TestPage extends WWW {
    private static final Object log = null;

    static class B extends W3 {
        public B() {
            <ref>log.hashCode();
        }
    }
}

class WWW extends W3{
    private static final Object log = new Object();
    protected void run(Object runnable){}
}
class W3 extends W4 {
    private static final Object log = new Object();
}
class W4 {
    private static final Object log = new Object();
}
