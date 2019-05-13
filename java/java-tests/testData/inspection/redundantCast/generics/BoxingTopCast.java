class TestCasting
{
  public void testRedundantCast() throws Exception
  {
    Object o = getObject();
    double d = 0.0;

    if( o instanceof Integer )
    {
      d = (double) (Integer)o;
    }

    System.out.println( "d = " + d );
  }

  private Object getObject()
  {
    return new Integer(42);
  }
}