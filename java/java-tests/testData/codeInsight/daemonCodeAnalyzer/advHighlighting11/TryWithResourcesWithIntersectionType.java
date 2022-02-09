import java.io.Closeable;

class WithIntersection {
  public static void main(String[] args) /* throws IOException */ {
    try (<error descr="Unhandled exception from auto-closeable resource: java.io.IOException">var x  = createResource()</error>) {
      System.out.println(x);
    }
  }

  private static <T extends Closeable & Runnable> T createResource() {
    return null;
  }

}