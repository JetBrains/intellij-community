import org.jetbrains.annotations.Contract;

class Scratch
{
  public static void main(String[] args)
  {
    maybeNull = true;
    if (!isTrue(getMaybeNull())) { }
    if (<warning descr="Condition '!isTrue(null)' is always 'true'">!<warning descr="Result of 'isTrue(null)' is always 'false'">isTrue(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>)</warning></warning>) { }
    if (<warning descr="Condition '!isTrue(true)' is always 'false'">!<warning descr="Result of 'isTrue(true)' is always 'true'">isTrue(true)</warning></warning>) { }
    if (<warning descr="Condition '!isTrue(false)' is always 'true'">!<warning descr="Result of 'isTrue(false)' is always 'false'">isTrue(false)</warning></warning>) { }
  }

  static Boolean maybeNull = null;
  static Boolean getMaybeNull()
  {
    return maybeNull;
  }

  @Contract(value = "null -> false; !null -> param1", pure = true)
  public static boolean isTrue(final Boolean value)
  {
    return value != null && value;
  }
}