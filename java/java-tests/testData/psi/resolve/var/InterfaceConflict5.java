public class InterfaceConflict5{
  public interface A{
    int a = 0;
  }


  public class B implements A{
    public int a = 0;
  }

  public static class C extends B
  implements A{
    void foo(){
      System.out.println("" + <ref>a);
    }
  }
}