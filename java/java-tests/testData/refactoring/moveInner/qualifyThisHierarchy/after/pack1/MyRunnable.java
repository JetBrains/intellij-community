package pack1;

public class MyRunnable {
    private DImpl d;

    public MyRunnable(DImpl d) {
        this.d = d;
    }

    public void run() {
    d.iAmProtected();
  }
}
