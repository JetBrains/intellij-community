public class Test {
    private abstract class Inner {
      Inner(String s){}
      abstract void bar();
    }

    public class InnerImpl extends Inner {
        InnerImpl(String s) {
            super(s);
        }

        @Override
        void bar() {
            //To change body of implemented methods use File | Settings | File Templates.
        }
    }
}