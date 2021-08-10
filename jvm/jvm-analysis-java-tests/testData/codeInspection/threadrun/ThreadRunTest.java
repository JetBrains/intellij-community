public class ThreadRunTest {
    public void doTest() {
        final Runnable runnable = new Runnable() {
            public void run() { }
        };
        final Thread thread = new Thread(runnable);
        thread.<warning descr="Calls to 'run()' should probably be replaced with 'start()'">run</warning>();
    }
}
