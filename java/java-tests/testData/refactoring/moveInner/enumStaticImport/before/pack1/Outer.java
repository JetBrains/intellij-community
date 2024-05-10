package pack1;

import static pack1.Outer.Inner.Type.A1;

public class Outer {
  public static void main(String[] args) {
    System.out.println(new Inner().getType());
  }

  public static class Inner {
    public String getType() {
      return A1.name();
    }

    enum Type {
      A1;
    }
  }
}