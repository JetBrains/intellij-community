// "Add exception to method signature" "true-preview"
class C {
  interface I {
    void a() throws InterruptedException;
  }

  {
    Callable<I> i = () -> {
      return new I() {
        public void a() throws InterruptedException {
          Thread.sleep(2000);
        }
      };
    };
  }
}
