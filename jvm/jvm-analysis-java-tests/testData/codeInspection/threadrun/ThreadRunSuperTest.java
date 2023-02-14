public class ThreadRunSuperTest {
  public void doTest() {
    new Thread("") {
      @Override
      public void run() {
        super.run();
      }
    };
  }
}