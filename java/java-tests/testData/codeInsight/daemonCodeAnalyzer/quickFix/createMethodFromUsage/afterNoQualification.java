// "Create method 'run'" "true"
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
        <selection></selection>
    }
}