public class InterfaceConflict7{
  public interface A{
    int a = 0;
  }


  public class B implements A{
    public int a = 0;
  }

  public static class C extends B{
    void foo(){
      System.out.println("" + <ref>a);
    }
  }
}