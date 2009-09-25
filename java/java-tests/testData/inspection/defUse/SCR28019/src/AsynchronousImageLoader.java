import java.util.Stack;

public class AsynchronousImageLoader extends Thread {
    private final Stack _tasks = new Stack();

    private void threadBody() throws InterruptedException {
        while (true) {
            final Runnable task;
            synchronized (this) {
                while (_tasks.isEmpty())
                    wait();
                task = (Runnable) _tasks.pop();
            }
            task.run();
        }
    }
}
