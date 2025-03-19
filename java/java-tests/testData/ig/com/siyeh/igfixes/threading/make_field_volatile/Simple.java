public class Simple
{
  private static Object s_instance;

  public static Object foo()
  {
    if<caret>(s_instance == null)
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
