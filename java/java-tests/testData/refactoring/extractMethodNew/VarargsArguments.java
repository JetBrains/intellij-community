public class VarargsArguments {
  public VarargsArguments(String ... strs) {}

  public void test1(VarargsArguments o, String... foo) {}
  void bar() {
    test1(new VarargsArguments(<selection> "", ""</selection>));
  }
}