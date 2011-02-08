public class InterfaceConflict1{
  public interface A{
    int a = 0;
  }

  public interface B{
    int a = 0;
  }

  public static class C
  implements A, B{
    static{
      System.out.println("" + <ref>a);
    }
  }
}