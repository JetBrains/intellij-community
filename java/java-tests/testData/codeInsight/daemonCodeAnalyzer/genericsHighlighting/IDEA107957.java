class Test1 {

  private static final Foo<Boolean> test = new Foo().method(Boolean.TRUE);

  public static void main(String[] args) {
    System.out.println(test);
  }

  public static class Foo<T> {
    public Foo<Boolean> method(boolean arg) {
      return null;
    }

    public <T extends Enum<T>> Foo<T> method(T arg) {
      return null;
    }
  }
}

class Test2 {

  private static final Foo<Boolean> test = Foo.method(Boolean.TRUE);

  public static void main(String[] args) {
    System.out.println(test);
  }

  public static class Foo<T> {
    public static Foo<Boolean> method(boolean arg) {
      return null;
    }

    public static <T extends Enum<T>> Foo<T> method(T arg) {
      return null;
    }
  }
}
