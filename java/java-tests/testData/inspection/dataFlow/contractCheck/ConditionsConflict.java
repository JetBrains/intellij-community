import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class Foo {
  @Contract(<warning descr="Contract clause 'null -> null' is unreachable: previous contracts cover all possible cases">"_ -> !null; null -> null"</warning>)
  public native String nonTrivialAfterTrivial(String x);

  @Contract(<warning descr="Contract clause '!null -> null' is never satisfied as its conditions are covered by previous contracts">"!null -> !null; !null -> null"</warning>)
  public native String repeating(String x);

  @Contract(<warning descr="Contract clause 'true, _, _ -> fail' is never satisfied as its conditions are covered by previous contracts">"true, false, _ -> !null; true, true, _ -> null; true, _, _ -> fail"</warning>)
  public native String booleanProblem(boolean x, boolean y, String z);

  @Contract("true, false, _ -> !null; true, true, _ -> null; false, _, _ -> fail")
  public native String booleanOk(boolean x, boolean y, String z);

  @Contract("true, false, _ -> !null; false, _, _ -> fail; true, true, _ -> null")
  public native String booleanOk2(boolean x, boolean y, String z);

  @Contract(<warning descr="Contract clause 'null, null, _, null, !null -> fail' is never satisfied as its conditions are covered by previous contracts">"null, null, null, null, null -> null; null, null, !null, null, _ -> null; null, null, null, null, !null -> !null; null, null, _, null, !null -> fail"</warning>)
  public native String test(String a, String b, String c, String d, String e);
}
