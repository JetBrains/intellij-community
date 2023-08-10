import org.jetbrains.annotations.NotNull;

class CloneReturnsClassType implements Cloneable {
  @NotNull
  @Override
  public CloneReturnsClassType clone() throws CloneNotSupportedException {
    return (CloneReturnsClassType)super.clone();
  }
}
class One { // no warning because not Cloneable
  @NotNull
  @Override
  protected Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
class Two implements Cloneable {
  @NotNull
  @Override
  protected <warning descr="'clone()' should have return type 'Two'">Object</warning> clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
class Three {
  @NotNull
  @Override
  protected <warning descr="'clone()' should have return type 'Three'">String</warning> clone() throws CloneNotSupportedException { // evil is reported even if not Cloneable
    return "super.clone()";
  }
}
class Anonymous {

  {
    B settings = new B() {
      public B clone() throws CloneNotSupportedException {
        return (B)super.clone();
      }

    };
  }

  class B implements Cloneable {}
}
class ThrowsException implements Cloneable {
  @NotNull
  @Override
  protected Object clone() throws CloneNotSupportedException {
    if (true) {
      System.out.println("clone");
    }
    throw new RuntimeException();
  }
}
class ReturnsNull implements Cloneable {
  @NotNull
  @Override
  protected Object clone() throws CloneNotSupportedException {
    return (null);
  }
}
