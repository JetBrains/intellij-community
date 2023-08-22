// "Create class 'Foo'" "true-preview"
import java.util.Collection;
public class Test {
  public Collection<? extends Fo<caret>o> getSomething() {
    return null;
  }
}