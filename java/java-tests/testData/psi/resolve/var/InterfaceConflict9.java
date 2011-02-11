public class InterfaceConflict9{
  public interface A{
    int a = 0;
  }

  public interface B{
    int a = 0;
  }

  public interface C{
    int a = 0;
  }

  public static class D
  implements A, B, C{
    void foo(){
      System.out.println("" + <ref>a);
    }
  }
}