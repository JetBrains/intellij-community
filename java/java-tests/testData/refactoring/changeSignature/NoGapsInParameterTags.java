public class MyClass3
{
  /**
   * This method does amazing things.
   *
   * @param a First parameter.
   * @param b Second parameter.
   * @param c Third parameter.
   *
   * @return A magic string.
   *
   * @since Blabla 1.2.
   */
  public String <caret>myMethod(int a, long b, boolean c)
  {
    return "Hi there!";
  }

  public static void main(String[] args)
  {
    System.out.println(new MyClass3().myMethod(1, "2", true));
  }
}