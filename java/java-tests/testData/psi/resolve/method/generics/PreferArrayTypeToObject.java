import java.util.Arrays;

public class MyClass
{
  public static <T> void process(T obj)
  {
    System.out.println("Processing object: " + obj);
  }

  public static <T> void process(T[] arr)
  {
    System.out.println("Processing array: " + Arrays.toString(arr));
  }

  public static void main(String[] args)
  {
    String[] myArray = new String[] { "a", "b", "c" };

    proc<ref>ess(myArray);  // Ctr-Left-Click on "process" takes me to the wrong method!!!
  }
}