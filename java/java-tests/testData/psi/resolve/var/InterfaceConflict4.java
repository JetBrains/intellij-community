public class InterfaceConflict4{
  public interface A{
    int a = 0;
  }


  public class B{
    public static final int a = 0;
  }

  public static class C extends B
  implements A{
    static{
      System.out.println("" + <ref>a);
    }
  }
}