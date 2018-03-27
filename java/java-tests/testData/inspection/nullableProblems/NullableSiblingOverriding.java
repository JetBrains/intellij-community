import org.jetbrains.annotations.*;

interface NotNullInt {
  @NotNull
  String getString();

  void setString(@Nullable String s);
}

abstract class MethodBase {
  @Nullable public String getString() {
    return null;
  }
}
abstract class ParamBase {
  public void setString(@NotNull String s) {}
}

abstract class MethodFail extends MethodBase implements <warning descr="Nullable method 'getString' from 'MethodBase' implements non-null method from 'NotNullInt'">NotNullInt</warning> { }

abstract class ParamFail extends ParamBase implements <warning descr="Non-null parameter 's' in method 'setString' from 'ParamBase' should not override nullable parameter from 'NotNullInt'">NotNullInt</warning> { }