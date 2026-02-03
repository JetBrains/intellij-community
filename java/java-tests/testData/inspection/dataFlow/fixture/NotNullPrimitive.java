import org.jetbrains.annotations.NotNull;

class Foo {
  public int getValue() {
    return 5;
  }

  public void nullcheck() {
    Integer x = getValue();
    System.out.println(<warning descr="Condition 'x == null' is always 'false'">x == null</warning> ? "NULL" : Integer.toHexString(x));
  }
}

class Bar {
  @NotNull
  public Integer getValue() {
    return 5;
  }

  public void nullcheck() {
    Integer x = getValue();
    System.out.println(<warning descr="Condition 'x == null' is always 'false'">x == null</warning> ? "NULL" : Integer.toHexString(x));
  }
}
