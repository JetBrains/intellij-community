package a;

import p.Parent;
import static q.Child.foo;

public class User {

  public static void main(String[] args) {
    Parent.foo();
    foo();
  }
}