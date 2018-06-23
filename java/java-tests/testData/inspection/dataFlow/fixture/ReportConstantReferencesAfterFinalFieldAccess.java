class Foo {
  private final boolean field = hashCode() == 2;

  public void main(boolean b) {
    if (!b) {
      System.out.println(<weak_warning descr="Value 'b' is always 'false'">b</weak_warning>);

    }
    if (field) {
      System.out.println(b);
    }
    if (b) {
      System.out.println(<weak_warning descr="Value 'b' is always 'true'">b</weak_warning>);
    }
  }

}

