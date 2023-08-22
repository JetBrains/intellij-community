// "Convert to a record" "false"

package my.annotation2;

@MyAnn
public class <caret>SomeService {
  private final String name;

  public SomeService(String name) {
    this.name = name;
  }
}

@interface MyAnn {
}