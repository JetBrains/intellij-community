package pack1;
class D {
  protected void iAmProtected() {
  }
}

public class DImpl extends D {
  public class MyRunnable {     
    public void run() {
      iAmProtected();
    }
  }
}

