public class Test {
    public class Inner {
      Inner(String s) throws java.lang.IOException {}
    }

    public class InnerImpl extends Inner {
        InnerImpl(String s) throws java.lang.IOException {
            super(s);
        }
    }
}