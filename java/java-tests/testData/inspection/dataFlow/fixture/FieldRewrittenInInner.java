class Tester
{
  public static void main(String[] args)
  {
    new Tester();
  }

  private boolean flag;

  private Tester()
  {
    // qualified ctor: should drop locality
    this.new Inner();
    if (flag)
      System.err.println("Is true.");
    else
      System.err.println("Is false");
  }

  private Tester(boolean b)
  {
    // unqualified ctor: should drop locality
    new Inner();
    if (flag)
      System.err.println("Is true.");
    else
      System.err.println("Is false");
  }

  class Inner
  {
    Inner()
    {
      flag = true;
    }
  }
}