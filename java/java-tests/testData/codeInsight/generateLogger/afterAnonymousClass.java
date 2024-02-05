import org.apache.log4j.Logger;

class A {
    private static final Logger log<caret> = Logger.getLogger(A.class);

    public void foo() {
        new Runnable() {
            @Override
            public void run() {
            }
        }
    }
}