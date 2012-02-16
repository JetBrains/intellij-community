import java.io.File;

public interface Zoo {
  void foo(String s, File file);
}

class ZooImpl implements Zoo {
  private Zoo delegate;
  @Override
  public void foo(String s, File file) {
    delegate.foo(<caret>);
  }
}
