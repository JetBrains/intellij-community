import javax.annotation.meta.*;

interface UnknownInterface {
  void foo(String s);
}

class ImplWithNotNull implements UnknownInterface {
  public void foo(@bar.NullableNick String s) {
    System.out.println(s.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>());
  }
}