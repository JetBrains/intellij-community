package pack1;

public class MyRunnable {
    private final DImpl d;

    public MyRunnable(DImpl d) {
        this.d = d;
    }

    public void run() {
    d.iAmProtected();
  }
}
