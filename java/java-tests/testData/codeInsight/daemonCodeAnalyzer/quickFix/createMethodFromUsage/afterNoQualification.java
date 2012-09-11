// "Create Method 'run'" "true"
class Bug {

  interface Foo<X> {
    void run(X x);
  }

  public static void main(String[] args) {
    new Foo<Bug>() {
      @Override
      public void run(Bug o) {
        o.run();
      }
    };
  }

    private void run() {
        <selection>//To change body of created methods use File | Settings | File Templates.</selection>
    }
}