class Test {

  protected final String init;
  protected final Runnable foo = new Runnable() {
    {
      Runnable r = () -> {
        new Runnable() {
          {
            System.out.println(<error descr="Variable 'init' might not have been initialized">init</error>);
          }
          @Override
          public void run() {

          }
        };
      };
    }
    @Override
    public void run() {

    }
  };

  public Test(String init) {
    this.init = init;
  }

  private void createClass() {
    new Thread() {
      {
        Runnable runnable1 = () -> System.out.println(init);
      }
    };
  }
}