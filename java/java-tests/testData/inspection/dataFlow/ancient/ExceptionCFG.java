import java.io.*;

class Test
{
  public static int myunusedfield1 = 0;
  // --Recycle Bin (3/29/02 6:44 PM)public static int myunusedfield2 = 0;
  public static int myunusedfield3 = 0;
  // --Recycle Bin (3/29/02 6:44 PM)public static int myunusedfield4 = 0;
  public static int myunusedfield5 = 0;


     void testMethod1() throws IOException {}
     void testMethod2() throws IOException {}

     public void main(String argv[])
     {
          boolean callingMethod1 = false;
          try
          {
            int i = (int) 0;
               callingMethod1 = true;
               testMethod1();
               callingMethod1 = false;
               testMethod2();
          }
          catch (IOException e)
          {
               if (callingMethod1)
                    System.err.println("Error while calling method 1");
               else
                    System.err.println("Error while calling method 2");
          }
     }
}
