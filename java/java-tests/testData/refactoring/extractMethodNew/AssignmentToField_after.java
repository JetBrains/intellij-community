import org.jetbrains.annotations.NotNull;

class Class1 {

  protected int i;

  void main() {
    var c = newMethod();
  }

    private @NotNull Class1 newMethod() {
        return new Class1() {{
            i = 5;
        }};
    }
}