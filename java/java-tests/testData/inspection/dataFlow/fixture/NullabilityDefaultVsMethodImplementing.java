interface UnknownInterface {
  void foo(String s);
}

@javax.annotation.ParametersAreNonnullByDefault
class ImplWithNotNull implements UnknownInterface {
  public void foo(String s) {
    System.out.println(s.hashCode());
  }
}