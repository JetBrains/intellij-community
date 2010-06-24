public class Test {

    public Test(Object a) {
    }

    public Test(int i) {
        this(new Integer(i));
    }

    public Test(long l) {
        this(new Long(l));
    }
}
