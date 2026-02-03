// "Add exception to method signature|->Change only this method" "true-preview"
class C {
  interface I {
    void a();
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
