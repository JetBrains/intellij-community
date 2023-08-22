public class Simple
{
  private static volatile Object s_instance;

  public static Object foo()
  {
    if(s_instance == null)
    {
      synchronized(Simple.class)
      {
        if(s_instance == null)
        {
          s_instance = new Object();
        }
      }
    }
    return s_instance;
  }
}
