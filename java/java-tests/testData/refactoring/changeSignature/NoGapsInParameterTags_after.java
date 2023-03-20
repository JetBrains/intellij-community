public class MyClass3
{
  /**
   * This method does amazing things.
   *
   * @param b Second parameter.
   * @param a First parameter.
   * @param c Third parameter.
   * @param d
   * @return A magic string.
   * @since Blabla 1.2.
   */
  public String myMethod(int b, long a, boolean c, short d)
  {
    return "Hi there!";
  }

  public static void main(String[] args)
  {
    System.out.println(new MyClass3().myMethod(1, "2", true, ));
  }
}