import java.util.concurrent.Semaphore;

public class MainClassWithThread {
    public static void main(String[] args) {
        final Semaphore s = new Semaphore(1);
        s.acquireUninterruptibly();

        Thread t = new Thread(new Runnable() {
            public void run() {
                s.release();

                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    //
                }
            }
        });
        t.start();

        s.acquireUninterruptibly();
    }
}
