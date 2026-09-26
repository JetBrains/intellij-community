public class Task implements Runnable {

    public static void invoke(Task<caret> task){
        task.run();
    }

    public void run() { }
}
