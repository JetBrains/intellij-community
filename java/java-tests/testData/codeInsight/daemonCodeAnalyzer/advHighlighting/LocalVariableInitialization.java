import java.io.*;
import java.net.*;
import java.util.*;

public class a  {
  static final a ai;
  int ii;

  static {
    <error descr="Variable 'ai' might not have been initialized">ai</error>.ii = 4;
    ai = null;
  }

  void f1(int i) {
    int j;
    i = <error descr="Variable 'j' might not have been initialized">j</error>;
  }
  void f2(int i) {
    int j;
    if (i ==2) j = 4;
    i = <error descr="Variable 'j' might not have been initialized">j</error>;
  }
  void f3(int i) {
    int j;
    if (i==3 && (j=i) != 9) {
      i = j+2;
    }
    else {
      i -= -1 - <error descr="Variable 'j' might not have been initialized">j</error>;
    }
  }
  void f4(int i) {

   final int dd;

   Runnable r = new Runnable() {
    public void run() {
     int j = <error descr="Variable 'dd' might not have been initialized">dd</error>;
    }
   };

   if (i == 3) dd = 5;
   else dd = 6;
  }
  void f5(int i) {
   final int k;
   class inner { 
     void f() {
       int j = <error descr="Variable 'k' might not have been initialized">k</error>;
     }
   }

  }
    void f6(int a){
      Object[] var;
      if (a > 0){
      }
      else{
        var = new Object[1];
      }
      System.out.println(<error descr="Variable 'var' might not have been initialized">var</error>);
    }


    void f7() {
        int k;
        try {
            k=0;
        } finally {
            if (<error descr="Variable 'k' might not have been initialized">k</error>==0) {
            }
        }
    }

    void f8(int n)
    {
        int k;
        while (n < 4) {
            k = n;
            break;
        }
        // k is not "definitely assigned" before this
        System.out.println(<error descr="Variable 'k' might not have been initialized">k</error>);  
    }

    void f9() {
        final int k;
        <error descr="Variable 'k' might not have been initialized">k</error>+=1;
    }
    void f10() {
        final int k;
        <error descr="Variable 'k' might not have been initialized">k</error>++;
        int i = <error descr="Variable 'i' might not have been initialized">i</error> + 1;
        int j = (j=2) == 1 || j==0 ? 1 : j;
    }

    void f11() {
        int x = 0;
        switch (x) {
            case 0:
                int y = 1;
                System.out.println(y);
                break;
            case 1:
                int z = <error descr="Variable 'y' might not have been initialized">y</error>;
                System.out.println(z);
                break;
        }
    }
    void f12() {
        switch (0) {
        case 0:
            int k=0;
        case 1:
            System.out.println(<error descr="Variable 'k' might not have been initialized">k</error>);
        }
    }

    public class AInner {
      class AI2 {}
      private AI2 myTitleRenderer = new AI2() {
        private String myLabel = "";

        public String getTreeCellRendererComponent(String value) {
            if (value instanceof String) {
                int i = myLabel.length();
            }
            return null;
        }
      };
    }

    void f13() {
        int i ;
        try {
            i = 0;
            if (i==0) throw new IOException();
        }
        catch (IOException e) {
            if (<error descr="Variable 'i' might not have been initialized">i</error>==0) return;
        }
    }

    abstract class X {
        class XException extends Exception{}
        class YException extends Exception{}
        class ZException extends Exception{}
        public void test() throws XException {
            final Object obj;
            try {
                obj = test1();
            }
            catch (YException fnf) {
            }
            finally {
                try {
                    test2();
                }
                catch (ZException eof) {
                }
            }
            <error descr="Variable 'obj' might not have been initialized">obj</error>.hashCode(); //can stay uninitialized
        }

        public abstract Object test1() throws YException, XException;

        public abstract void test2() throws XException, ZException;
    }

    public static int test(List aList) {
        List list2;
        int counter = 0;
        for (int i=0; i<aList.size(); i++) {
            while (counter != 0) {
                counter++;
                list2 = new ArrayList();
            }
            <error descr="Variable 'list2' might not have been initialized">list2</error>.add(aList.get(i));
        }
        return counter;
    }

    void forEachParam(java.io.File x) {
        for (java.io.File f: <error descr="Variable 'f' might not have been initialized">f</error>.listFiles()) {
           forEachParam(f);
        }
    }

  // all code below is correct
  int cf1(int i) {
    return i;
  }

  void cf2(int i) {
    int j;
    if (i == 0 && (j=i) != 2) {
      i = j;
    }
    if (i == 0 || (j=i) != 2 || j>3) {
      i = 2;
    }
  }

  boolean cf3(int i) {
    final int j;
    if (i<3 || i>33) j = i;
    else j = 4;
    i = j;

    return i==3 && i==5;
  }
  void cf33(int i) {
    final int j2;
    while (true) {
      j2 = 5;
      break;
    }
    i = j2;
  }

  void cf4() {
    final int i1;
    int i2;
    final Object o1;
  }
  void cf5() {
   final int dialog = 3;

   final int dd;
   if (dialog == 3) dd = 5;
   else dd = 6;

   Runnable r = new Runnable() {
    public void run() {
     int i = dialog;
     int j = dd;
    }
   };

  }
  void cf6() {
    class inner extends a {
      void fi() {
       int i = ii;
      }
    }
    a ainst = new a() {
      void fi() {
        int i = ii;
      }
    };
  }
  a() {
    int i = ai.ii;
  }
  void cf7() {
      for(int i = 0; i < 3; i++){
        Object element;
        if (i==0){
          element = null;
        }
        else if (i==3){
          element = null;
        }
        else{
          continue;
        }
        Object newe = element;
      }
  }
  void cf8(int n)
   {
        int i;
        while (true) {
            if (false) {
              i = 0;
              break;
            }
        }
        i++;

    }
  final boolean FB = true;

  void cf9() {
      int k;
      if (FB) {
        k = 4;
      }
      int j = k;
  }


  void cf10() {
    for (String line; (line = "") != null; ) {
      line.indexOf(" ");
    }
  }

  void cf11(boolean d) {
        boolean b;
        boolean c = true;
        if (c && (false && true)) {
            c = b;
        }
  }
  void cf12() {
        boolean booleanVar = true;
        boolean stringVar;
        if (!(booleanVar && (stringVar = true))) {
            stringVar = false;
        }
        if (stringVar) {

        }
  }
  
    void cfxx(boolean a, boolean b) {
        int n;
        if ((a || b) && (n = 0) >= 2) {
            n++;   //
        }
    }
    void cfxx1(boolean a, int b) {
        final int i;
        if ((true || false) && (i = b) != 0) {
            System.out.println(i); // i gets highlighted }
        }
    }
    void cfx3() {
        boolean b;
        boolean c;// = true;
        if (c && false) {
            c = b;
        }
    }
    void cfx4()
    {
        final int k;
        if (false) {
            k = 0;
            k = 1;
            System.out.println(k);
        }
    }
}


class Main {
     void f() {
         final int x;
         x = 0;
         class C {
             void m () {
                 int y = x;
             }
         }
     }
}


// continue in finally
class QuartzSchedulerThread {
    public void run() throws IOException {
        while (true) {
            try {
            } finally {
                try {
                    run();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                continue;
            }
        }
    }

}
class ExceptionProblems {

	private boolean bad() {
		final boolean succeeded;
		try {
            new FileInputStream("test");
            succeeded = true;
		} catch (IOException e) {
			<error descr="Variable 'succeeded' might already have been assigned to">succeeded</error> = false; // should warn here
		}
		return succeeded;
	}
}
class ImGood {
        int foo() {    //IDEADEV-7446 
            int foo;
            if (true) {
                foo = 42;
            }
            return foo;
        }
    }

class SwitchTest
{
    public static String method()
    {
        int a = 0;
        switch (a)
        {
            case 0:
                return null;
            case 4:
                String description;
                return <error descr="Variable 'description' might not have been initialized">description</error>;
            default:
                return "";
        }
    }
}