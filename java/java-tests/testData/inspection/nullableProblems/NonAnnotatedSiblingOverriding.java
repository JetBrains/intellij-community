import org.jetbrains.annotations.*;

interface NotNullInt {
  void setString(@NotNull String s);
}

interface NonAnnotatedInt {
  void setString2(String s);
}

abstract class ParamBase {
  public void setString(String s) {}
  public void setString2(@NotNull String s) {}
}

abstract class ParamFail extends ParamBase implements <warning descr="Non-annotated parameter 's' in method 'setString' from 'ParamBase' should not override non-null parameter from 'NotNullInt'">NotNullInt</warning> { }

abstract class ParamFail2 extends ParamBase implements <warning descr="Non-null parameter 's' in method 'setString2' from 'ParamBase' should not override non-annotated parameter from 'NonAnnotatedInt'">NonAnnotatedInt</warning> { }

