class Test {
  public <T> void foo(T p) {
    System.out.println(p.getClass().getName());
  }

  public static void main(String[] args) {
    new Test().foo(10f);
  }

}