
import java.util.List;

class CloseAction {
  public void performAction() throws IOException {
  }
}

class Foo<T extends Foo<?>>{
  void performAction() {
  }

  void bar(List<? extends Foo<?>> f) {
      Foo<?> m = f.get(0);
      m.performAction();
  }
}
