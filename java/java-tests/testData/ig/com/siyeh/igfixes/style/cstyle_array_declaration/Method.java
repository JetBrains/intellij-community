public class Method {

  public String/*2*/ test1(String[] a)/*1*/[]<caret> {
    return a;
  }

  public @Required Integer @Required @Preliminary[] test2() @Required   @Preliminary[] {
    return null;
  }

  public @Required Integer test3() @Required @Preliminary[] @Preliminary[]   {
    return null;
  }

  public Integer @Required   @Preliminary[] test4() [] {
    return null;
  }

  public Integer [] test5() @Required []  []  {
    retun null;
  }
}