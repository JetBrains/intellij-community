class AsynchronousImageLoader extends Thread {
    interface Stack {
        boolean isEmpty();
        Object pop();
    }

    private void threadBody(Stack _tasks) throws InterruptedException {
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
