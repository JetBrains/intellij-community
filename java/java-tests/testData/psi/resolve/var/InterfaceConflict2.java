public class InterfaceConflict2{
  public interface A{
    int a = 0;
  }

  public interface B{
    int a = 0;
  }

  public interface E extends A{
  }

  public static class C
  implements E, B{
    static{
      System.out.println("" + <ref>a);
    }
  }
}