public class Bar {
    public int baz(byte blah, byte blah1, byte blah2) {
        return <selection>blah + blah1 + blah2</selection>;
    }
}
class S extends Bar {
    public int baz(byte blah, byte blah1, byte blah2) {
        System.out.println(blah1);
        return super.baz((byte) 0, (byte) 0, (byte) 0);    //To change body of overridden methods use File | Settings | File Templates.
    }
}