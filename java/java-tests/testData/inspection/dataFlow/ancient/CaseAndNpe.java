package test.deadcode;

import java.io.IOException;

public class aaa {
  Object a;
  int b;

  void x(int i, Object o) {
    if (o == null) {
      i = 2;
    }

    if (o instanceof String) {
      o.equals(o);
    }

    o.<warning descr="Method invocation 'equals' may produce 'NullPointerException'">equals</warning>(o);

    if (i == 3) {
      System.out.println("");
    }
    if (i == 3) {
      o.equals(o);
      o.equals(o);
    }

    o.equals(o);


    if (i == 2) {
      i = 6;
    } else {
      i = 5;
    }

    //System.exit(0);

    switch(i) {
      case <warning descr="Switch label '1' is unreachable">1</warning>: System.out.println("1 not reachable"); break;
      case <warning descr="Switch label '2' is unreachable">2</warning>: System.out.println("2 not reachable"); break;
      case 6: System.out.println("6 reachable"); break;
      case 5: System.out.println("5 reachable"); break;
      default: System.out.println("Default not reachable"); break;
    }
    int j = 0;

    for (; <warning descr="Condition 'i < 5' is always 'false'">i < 5</warning>; i++, j++) {}
  }

  void canBeStatic() {
    for (int i = 15; i >= 0; --i)
    {
      if ((i==4) || (i==10))
      {
        //actual block body removed
      }
    }

    for (int i = 0; <error descr="Operator '<' cannot be applied to 'int', 'null'">i < null</error>; i++) {
      a = i;
      this.a = i;
    }

    if (a != b && a != 5) {
      a = 3;
    } else {
      a = 4;
    }

    if (<warning descr="Condition 'a != null && a instanceof aaa' is always 'false'"><warning descr="Condition 'a != null' is always 'true'">a != null</warning> && <warning descr="Condition 'a instanceof aaa' is always 'false' when reached">a instanceof aaa</warning></warning>) {
      if (<warning descr="Condition is always true">true</warning>) {
        //a = new aaa();
      }
    }

    a = 6 + 1;
  }
}


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

