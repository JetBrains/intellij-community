public class InterfaceConflict6{
  public interface A{
    int a = 0;
  }


  public class B{
    public int a = 0;
  }

  public static class C extends B
  implements A{
    void foo(){
      System.out.println("" + <ref>a);
    }
  }
}