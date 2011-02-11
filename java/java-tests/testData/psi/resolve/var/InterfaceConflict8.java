public class InterfaceConflict8{
  public interface A{
    int a = 0;
  }


  public static class B implements A{
  }

  public static class C extends B
  implements A{
    void foo(){
      System.out.println("" + <ref>a);
    }
  }
}