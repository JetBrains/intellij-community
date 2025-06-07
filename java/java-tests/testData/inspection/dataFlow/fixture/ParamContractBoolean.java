import org.jetbrains.annotations.Contract;

class Scratch
{
  public static void main(String[] args)
  {
    maybeNull = true;
    if (<warning descr="Condition '!isTrue(getMaybeNull())' is always 'false'">!isTrue(<warning descr="Result of 'getMaybeNull()' is always 'true'">getMaybeNull()</warning>)</warning>) { }
    unknown();
    if (!isTrue(getMaybeNull())) { }
    if (<warning descr="Condition '!isTrue(null)' is always 'true'">!isTrue(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>)</warning>) { }
    if (<warning descr="Condition '!isTrue(true)' is always 'false'">!isTrue(true)</warning>) { }
    if (<warning descr="Condition '!isTrue(false)' is always 'true'">!isTrue(false)</warning>) { }
  }
  
  static native void unknown();

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