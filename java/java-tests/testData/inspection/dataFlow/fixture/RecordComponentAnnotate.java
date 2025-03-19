import org.jetbrains.annotations.*;

record VarAccess(String name, @Nullable Expression arrayIndex, String member) {
  public static VarAccess scalar(String name) {
    return new VarAccess(name, null, <warning descr="Passing 'null' argument to non-annotated parameter"><caret>null</warning>);
  }
  public static VarAccess array(String name, Expression arrayIndex) {
    return new VarAccess(name, arrayIndex, <warning descr="Passing 'null' argument to non-annotated parameter">null</warning>);
  }
}

interface Expression {}