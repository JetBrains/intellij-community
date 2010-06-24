// unreachables
import java.io.*;
import java.net.*;
public class a  {
interface ii {}

 { // initializer
   try {
     throw new Exception();
     <error descr="Unreachable statement">int i = 5;</error>
   }
   catch (Exception e) {
   }
 }


 int f1() throws Exception {
   return 2;
   <error descr="Unreachable statement">return 5;</error>
 }
 int f2(int i) throws Exception {
   if (i==2) return 2;
   throw new Exception();
   <error descr="Unreachable statement">return 5;</error>
 }
 int f3(int i) throws Exception {
   for (;;) return 2;
   <error descr="Unreachable statement">return 5;</error>
 }
 int f4(int i) throws Exception {
   try {
     if (i==3) throw new Exception();
     return 4;
   } finally {
     if (i==6) return 9;
   }
   <error descr="Unreachable statement">return 5;</error>
 }

 void f5()
    {
        try {
        } catch (Error e) {
            throw e;
            <error descr="Unreachable statement">;</error>
        }
    }

 void f6() {
    try {
    }
    finally {
      return;
      <error descr="Unreachable statement">;</error>
    }
 }

  void f7(RuntimeException e) {
    for (;;) {
      if (e==null) {
        return;
        <error descr="Unreachable statement">break;</error>
      }
    }
  }
  void f8(RuntimeException e,int i,int j,int k,int l) {
    for (;;) {
      if (e==null) {
        return;
        <error descr="Unreachable statement">f8(e,i,j,k,l);</error>
      }
    }
  }
  void f9(RuntimeException e,int i,int j,int k,int l) {
    for (;;) {
      if (e==null) {
        return;
        <error descr="Unreachable statement">throw e;</error>
      }
    }
  }
  class Af10 {
    int f() {
        return 0;
        <error descr="Unreachable statement">new Af10();</error>
    }

  }
  class Af11 {
    int f() {
        return 0;
        <error descr="Unreachable statement">int k;</error>
    }
    void test() {
        int i;
        return;
        <error descr="Unreachable statement">assert i == i;</error>
    }
  }



 int cf1(int i) throws Exception {
   if (i==2) return 2;
   for (int k=0;1==1;k++) break;
   try {
    i = 5;
   } 
   catch (Error e) {
     if (i==5) return 2;
   }
   catch (RuntimeException e) {
   }
   catch (Throwable e) {
   }

   return 5;
 }

 int cf2(int i) throws Exception {
   switch (i) {
     case 1: return 4;
   }
   return 5;
 }
 void cf3() {
   if (false) {
     int i = 5;
   }
   while (true) { break; }
   if (true) {
     int i = 4;
   }
   else {
     int i = 6;
   }
 }
 void cf4() throws java.net.SocketException {
  try {
    bind();
  } catch (java.net.SocketException se) {
    throw se;
  } catch(java.io.IOException e) {
    throw new java.net.SocketException(e.getMessage());
  }
 }
 void bind() throws java.net.SocketException {}


 void cf5() {
  try {
  } catch(Exception e) {
    
  }
 }

 void cf6() {
        if (true || true) {
        } else {
            System.out.println("hi");
        }
 }


    static boolean g() {
        return true;
    }
    public static void main(String[] args) {
        boolean b=false && g();
        System.out.println("b = "+b);
    }



 void cf7() {
        try {
        } finally {
            try {
            } finally {
            }
            try {
            } finally {
                try {
                } finally {
                }
                try {
                } finally {
                    try {
                    } finally {
                        try {
                        } finally {
                            try {
                            } finally {
                                try {
                                } finally {
                                }
                            }
                        }
                    }
                    try {
                        try {
                        } finally {
                        }
                    } finally {
                    }
                }
            }
        }
        ;
 }

 void cf8() {
  class Test01
  {
 
  public int testMethod()
  {
    try
    {
      throw new Exception("test");
    }
    catch(Exception e)
    {
      try
      {
        throw new Exception("test");
      }
      catch(Exception ee)
      {}
      finally
      {}
    }
    finally
    {
 
      try
      {
        throw new Exception("test");
      }
      catch(Exception e)
      {}
      finally
      {}
    }
    return 0;
  }
  }
 }

 void cf9() {
      // should not try to compute constant expression within assert
      // since assertions can be disabled/enabled at any moment via JVM flags
   assert true;
   assert false;
   final int i;
   if (0==1) {
     i = 9;
   }
   else {
     i = 0;
   }
 }

 void cf10() {
   int k = 0;
   switch (k) {
   }
 }


  private Exception getException()
  {
    return new java.io.IOException();
  }

  public void cf11()
  {
    try {
      throw getException();
    }
    catch (java.io.IOException e) {
      e.printStackTrace();  // IDEA claims this to be "Unreachable statement"//
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  //////////////////////////
    public void cf12() {
        try {
            try {
            } finally {
                doit();
            }
        } catch (Excep1 e) {
            String error = "RollbackException";
        } catch (Excep2 e) {
            String error = "HeuristicRollbackException";
        } catch (Excep3 e) {
            String error = "SystemException";
        } catch (IllegalStateException e) {
            String error = "IllegalStateException";
        } catch (RuntimeException e) {
            String error = "RuntimeException";
        }
    }

    private void doit() throws Excep1, Excep2, Excep3{
        //To change body of created methods use Options | File Templates.
    }

   class Excep1 extends Exception {}
   class Excep2 extends Exception {}
   class Excep3 extends Exception {}




    public void cf13() throws Exception {
        while (true) {
            try {
                cf13();
            } finally {
                continue;
            } 
        }
    }

}


class NestedFinallyTest {
    void ftest4() {
        try {
        }
        finally {
            try {
            }
            finally {
                try {
                }
                finally {
                    try {
                    }
                    catch (Throwable t4) {
                    }
                }
            }
        }
        ftest4();
    }
}






