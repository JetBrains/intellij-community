@javax.annotation.concurrent.Immutable
interface Intf {
  @org.jetbrains.annotations.Nullable String foo();
}

class Usage {
  void bar(Intf i) {
    if (i.foo() != null) {
      System.out.println(i.foo().length());
    }
  }

}