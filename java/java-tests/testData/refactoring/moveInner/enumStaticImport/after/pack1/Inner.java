package pack1;

import static pack1.Inner.Type.A1;

public class Inner {
  public String getType() {
    return A1.name();
  }

  enum Type {
    A1;
  }
}
