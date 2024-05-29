class Main {

  public static void main(String[] args) {
    System.out.println(Foo.valueOf(args[0]).getSomething());
  }

  public enum Foo {

    ONE {
      @Override
      public String getSomething() {
        return "ONE";
      }
    },
    TWO {
      @Override
      public String getSomething() {
        return "TWO";
      }
    };

    public abstract String getSomething();
  }
}