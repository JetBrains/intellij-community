public class Bar {
    public int baz(byte blah1, int anObject) {
        return anObject;
    }
}
class S extends Bar {
    public int baz(byte blah1, int anObject) {
        System.out.println(blah1);
        return super.baz((byte) 0, anObject);    //To change body of overridden methods use File | Settings | File Templates.
    }
}