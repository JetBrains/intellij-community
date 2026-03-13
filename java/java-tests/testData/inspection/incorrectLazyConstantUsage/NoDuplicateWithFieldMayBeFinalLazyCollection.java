import java.util.List;

class Main {
  private List<String> <warning descr="Lazy collection field should be 'final'">b</warning>=List.ofLazy(3,i ->"item"+i);
}