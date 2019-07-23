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

  private Tester(int i)
  {
    // nested class: should not drop locality ('this' is not leaked)
    new Nested();
    if (<warning descr="Condition 'flag' is always 'false'">flag</warning>)
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

  static class Nested
  {
    Nested()
    {
      <error descr="Non-static field 'flag' cannot be referenced from a static context">flag</error> = true; // cannot access flag
    }
  }
}