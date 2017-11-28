import javax.annotation.meta.*;

@TypeQualifierNickname()
@javax.annotation.Nonnull(when = When.MAYBE)
@interface NullableNick {}

interface UnknownInterface {
  void foo(String s);
}

class ImplWithNotNull implements UnknownInterface {
  public void foo(@NullableNick String s) {
    System.out.println(s.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>());
  }
}