import org.jetbrains.annotations.*;

record VarAccess(String name, @Nullable Expression arrayIndex, @Nullable String member) {
  public static VarAccess scalar(String name) {
    return new VarAccess(name, null, <caret>null);
  }
  public static VarAccess array(String name, Expression arrayIndex) {
    return new VarAccess(name, arrayIndex, null);
  }
}

interface Expression {}