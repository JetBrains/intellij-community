public class InterfaceConflict10{
  public interface A{
    int a = 0;
  }

  public static class B
  implements A{
  }

  public interface C{
    int a = 0;
  }

  public static class D extends A
  implements A, C{
    void foo(){
      System.out.println("" + <ref>a);
    }
  }
}