// "Add exception to method signature" "true-preview"
class C {
  interface I {
    void a() throws InterruptedException;
  }

  {
    I i = () -> {
      Thread.sleep(2000);
    };
  }
}
