public class ThreadRunQfTest {
    public void doTest() {
        final Runnable runnable = new Runnable() {
            public void run() { }
        };
        final Thread thread = new Thread(runnable);
        thread.ru<caret>n();
    }
}
