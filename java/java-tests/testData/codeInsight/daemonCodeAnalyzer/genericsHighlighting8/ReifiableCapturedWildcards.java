import java.util.List;

class Test {
  public void bar(List<?> list) {
    foo(list.get(0));
  }

  private final <K>void foo(K... <warning descr="Parameter 'k' is never used">k</warning>) {}
}