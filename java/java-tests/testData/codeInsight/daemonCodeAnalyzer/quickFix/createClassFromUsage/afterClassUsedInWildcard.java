// "Create class 'Foo'" "true-preview"
import java.util.Collection;
public class Test {
  public Collection<? extends Foo> getSomething() {
    return null;
  }
}

public class <caret>Foo {
}