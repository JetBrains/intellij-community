public class MyRunnable implements Runnable {

    private class MyAnotherRunnable extends MyRunnable {}

    Runnable foo() {
        return new <caret>
    }
}