class Foo {
  private final boolean field = hashCode() == 2;

  public void main(boolean b) {
    if (!b) {
      System.out.println(<warning descr="Value 'b' is always 'false'">b</warning>);

    }
    if (field) {
      System.out.println(b);
    }
    if (b) {
      System.out.println(<warning descr="Value 'b' is always 'true'">b</warning>);
    }
  }

}

