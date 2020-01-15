import ambiguous.*;

class Test implements Foo {
  @Override
  public String @NotNull [] getStrings() {
    return new String[] {null};
  }
}

interface Foo {
  String[] getStrings();
}