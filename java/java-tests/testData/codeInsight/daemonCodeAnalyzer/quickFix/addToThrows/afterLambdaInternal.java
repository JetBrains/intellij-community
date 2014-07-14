// "Add Exception to Method Signature" "true"
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
