import org.jetbrains.annotations.NotNull;

public class VarargsArguments {
  public VarargsArguments(String ... strs) {}

  public void test1(VarargsArguments o, String... foo) {}
  void bar() {
    test1(new VarargsArguments(newMethod()));
  }

    private String @NotNull [] newMethod() {
        return new String[]{"", ""};
    }
}