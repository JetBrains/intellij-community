//Missing return statement
import java.io.*;
import java.net.*;
public class a  {
interface ii {}



 int f1() throws Exception {
 <error descr="Missing return statement">}</error>
 
 Object f2(int i) throws Exception {
   if (i == 0) return null;
 <error descr="Missing return statement">}</error>

 Object f3(int i) throws Exception {
   while (i == 0) return null;
 <error descr="Missing return statement">}</error>

 Object f4(int i) throws Exception {
   switch (i) {
     case 1: return null;
   }
 <error descr="Missing return statement">}</error>

 Object f5(int i) throws Exception {
   if (i==2) return null;
   else if (i != 2) return null;
 <error descr="Missing return statement">}</error>

 Object f6(int i) throws Exception {
   if (true) return null;
 <error descr="Missing return statement">}</error>

 int f7(int i) {
  try {
   if (i==2) return 4;
   else throw new IllegalArgumentException();
  } catch (Exception e) {
  }
 <error descr="Missing return statement">}</error>

  int f8(int i) {
    try {
      //throw new Error();
    }
    finally {
      try {
        //throw new Exception();
      }
      catch (Exception e) {
        return 5;
      }
    }
 <error descr="Missing return statement">}</error>

 int cf1(int i) {
   return 0;
 }
 int cf2(int i) {
   if (i==2) return 0;
   else if (i==4) return -1;
   else return 2;
 }
 int cf3(int i) {
   return i==2 ? 3 : 5;
 }
 int cf4(int i) {
   switch (i) {
    case 1: return 4;
    case 3: return 6;
    default: return 5;
   }
 }
 int cf5(int i) {
   if (i>1) {
    if (i==4) return 0;
    else return i==3 ? 2 : 0;
   }
   else return 2;
 }
 int cf6(int i) {
   return cf4(i+1);
 }

 int cf7(int i) throws Exception {
   try {
     throw new Exception();
   } catch(Error e) {
     return 3;
   } finally {
     return 2;
   }

 }

 int cf8(int i) throws Exception {
   try {
     return 2;
   } finally {
     return 4;
   }
 }
 int cf9(int i) throws Exception {
   try {
     i = 5;
   } finally {
     throw new Exception();
   }
 }

 int cf10(int i) {

   while (true)
     return 0;
 }
 int cf11(int i) {

  // commented out reference
  // does not work when running under JRE

   while (a.co != 2 && 1+3/2-1 + (int)1.5 + 2%2 == 2 /* && 0x7fffffff == Integer.MAX_VALUE */ && ('c' & 0x00) == 0)
     return 0;
 }
 private static final int co = 1/2 + 1;
 int cf12(int i) {

   for (int k=0; (0xf0 | 0x0f) == 0xff && false != true && co == 1;k++)
     return 0;
 }


 int cf13() {
 try {
  try {
   throw new IllegalArgumentException();
   //throw new java.io.IOException();
  } catch (IllegalArgumentException e) {
   return 3;
  }
  finally {
   throw new java.io.IOException();
  }

 } catch (java.io.IOException ee) {
   return 88;
 }
 }

 int cf14() {
   try {
     cf13();
     return 13;
   } finally {
     cf13();
   }
 }
 
 int cf15() {
  try {
   int i=0;
   return i;
  } catch (Exception e) {
  } finally {
  }
  return 0;
 }
 int cf16() {
    try {
      if ( ! (1==3)) {
          return 0;
      }
    } finally {
        // Restore the current position of the other DynAny
    }
    return 1;
 }

 int cf17() {
        try {
            try {
                return 0;
            } finally {
            }
        } finally {
        }
  }
 int cf18(int i) {
   int k;
   try {
      if (i==4) return 0;
      k = 4;
   } finally {
   }
   return k;
  }


 void cf19() {

      try {

        try {
        }
        finally {
        }
          return ;
      }
      catch (Exception e) {
      }
 }

}

 class a2  {
interface ii {}



 int f1() throws Exception {
 <error descr="Missing return statement">}</error>
 
 Object f2(int i) throws Exception {
   if (i == 0) return null;
 <error descr="Missing return statement">}</error>

 Object f3(int i) throws Exception {
   while (i == 0) return null;
 <error descr="Missing return statement">}</error>

 Object f4(int i) throws Exception {
   switch (i) {
     case 1: return null;
   }
 <error descr="Missing return statement">}</error>

 Object f5(int i) throws Exception {
   if (i==2) return null;
   else if (i != 2) return null;
 <error descr="Missing return statement">}</error>

 Object f6(int i) throws Exception {
   if (true) return null;
 <error descr="Missing return statement">}</error>

 int f7(int i) {
  try {
   if (i==2) return 4;
   else throw new IllegalArgumentException();
  } catch (Exception e) {
  }
 <error descr="Missing return statement">}</error>

  int f8(int i) {
    try {
      //throw new Error();
    }
    finally {
      try {
        //throw new Exception();
      }
      catch (Exception e) {
        return 5;
      }
    }
 <error descr="Missing return statement">}</error>

 int cf1(int i) {
   return 0;
 }
 int cf2(int i) {
   if (i==2) return 0;
   else if (i==4) return -1;
   else return 2;
 }
 int cf3(int i) {
   return i==2 ? 3 : 5;
 }
 int cf4(int i) {
   switch (i) {
    case 1: return 4;
    case 3: return 6;
    default: return 5;
   }
 }
 int cf5(int i) {
   if (i>1) {
    if (i==4) return 0;
    else return i==3 ? 2 : 0;
   }
   else return 2;
 }
 int cf6(int i) {
   return cf4(i+1);
 }

 int cf7(int i) throws Exception {
   try {
     throw new Exception();
   } catch(Error e) {
     return 3;
   } finally {
     return 2;
   }

 }

 int cf8(int i) throws Exception {
   try {
     return 2;
   } finally {
     return 4;
   }
 }
 int cf9(int i) throws Exception {
   try {
     i = 5;
   } finally {
     throw new Exception();
   }
 }

 int cf10(int i) {

   while (true)
     return 0;
 }
 private static final int co = 1/2 + 1;
 int cf12(int i) {

   for (int k=0; (0xf0 | 0x0f) == 0xff && false != true && co == 1;k++)
     return 0;
 }


 int cf13() {
 try {
  try {
   throw new IllegalArgumentException();
   //throw new java.io.IOException();
  } catch (IllegalArgumentException e) {
   return 3;
  }
  finally {
   throw new java.io.IOException();
  }

 } catch (java.io.IOException ee) {
   return 88;
 }
 }

 int cf14() {
   try {
     cf13();
     return 13;
   } finally {
     cf13();
   }
 }
 
 int cf15() {
  try {
   int i=0;
   return i;
  } catch (Exception e) {
  } finally {
  }
  return 0;
 }
 int cf16() {
    try {
      if ( ! (1==3)) {
          return 0;
      }
    } finally {
        // Restore the current position of the other DynAny
    }
    return 1;
 }

 int cf17() {
        try {
            try {
                return 0;
            } finally {
            }
        } finally {
        }
  }
 int cf18(int i) {
   int k;
   try {
      if (i==4) return 0;
      k = 4;
   } finally {
   }
   return k;
  }


 void cf19() {

      try {

        try {
        }
        finally {
        }
          return ;
      }
      catch (Exception e) {
      }
 }

}


 class a3  {
interface ii {}



 int f1() throws Exception {
 <error descr="Missing return statement">}</error>
 
 Object f2(int i) throws Exception {
   if (i == 0) return null;
 <error descr="Missing return statement">}</error>

 Object f3(int i) throws Exception {
   while (i == 0) return null;
 <error descr="Missing return statement">}</error>

 Object f4(int i) throws Exception {
   switch (i) {
     case 1: return null;
   }
 <error descr="Missing return statement">}</error>

 Object f5(int i) throws Exception {
   if (i==2) return null;
   else if (i != 2) return null;
 <error descr="Missing return statement">}</error>

 Object f6(int i) throws Exception {
   if (true) return null;
 <error descr="Missing return statement">}</error>

 int f7(int i) {
  try {
   if (i==2) return 4;
   else throw new IllegalArgumentException();
  } catch (Exception e) {
  }
 <error descr="Missing return statement">}</error>

  int f8(int i) {
    try {
      //throw new Error();
    }
    finally {
      try {
        //throw new Exception();
      }
      catch (Exception e) {
        return 5;
      }
    }
 <error descr="Missing return statement">}</error>

 int cf1(int i) {
   return 0;
 }
 int cf2(int i) {
   if (i==2) return 0;
   else if (i==4) return -1;
   else return 2;
 }
 int cf3(int i) {
   return i==2 ? 3 : 5;
 }
 int cf4(int i) {
   switch (i) {
    case 1: return 4;
    case 3: return 6;
    default: return 5;
   }
 }
 int cf5(int i) {
   if (i>1) {
    if (i==4) return 0;
    else return i==3 ? 2 : 0;
   }
   else return 2;
 }
 int cf6(int i) {
   return cf4(i+1);
 }

 int cf7(int i) throws Exception {
   try {
     throw new Exception();
   } catch(Error e) {
     return 3;
   } finally {
     return 2;
   }

 }

 int cf8(int i) throws Exception {
   try {
     return 2;
   } finally {
     return 4;
   }
 }
 int cf9(int i) throws Exception {
   try {
     i = 5;
   } finally {
     throw new Exception();
   }
 }

 int cf10(int i) {

   while (true)
     return 0;
 }
 private static final int co = 1/2 + 1;
 int cf12(int i) {

   for (int k=0; (0xf0 | 0x0f) == 0xff && false != true && co == 1;k++)
     return 0;
 }


 int cf13() {
 try {
  try {
   throw new IllegalArgumentException();
   //throw new java.io.IOException();
  } catch (IllegalArgumentException e) {
   return 3;
  }
  finally {
   throw new java.io.IOException();
  }

 } catch (java.io.IOException ee) {
   return 88;
 }
 }

 int cf14() {
   try {
     cf13();
     return 13;
   } finally {
     cf13();
   }
 }
 
 int cf15() {
  try {
   int i=0;
   return i;
  } catch (Exception e) {
  } finally {
  }
  return 0;
 }
 int cf16() {
    try {
      if ( ! (1==3)) {
          return 0;
      }
    } finally {
        // Restore the current position of the other DynAny
    }
    return 1;
 }

 int cf17() {
        try {
            try {
                return 0;
            } finally {
            }
        } finally {
        }
  }
 int cf18(int i) {
   int k;
   try {
      if (i==4) return 0;
      k = 4;
   } finally {
   }
   return k;
  }


 void cf19() {

      try {

        try {
        }
        finally {
        }
          return ;
      }
      catch (Exception e) {
      }
 }

}



 class a4  {
interface ii {}



 int f1() throws Exception {
 <error descr="Missing return statement">}</error>
 
 Object f2(int i) throws Exception {
   if (i == 0) return null;
 <error descr="Missing return statement">}</error>

 Object f3(int i) throws Exception {
   while (i == 0) return null;
 <error descr="Missing return statement">}</error>

 Object f4(int i) throws Exception {
   switch (i) {
     case 1: return null;
   }
 <error descr="Missing return statement">}</error>

 Object f5(int i) throws Exception {
   if (i==2) return null;
   else if (i != 2) return null;
 <error descr="Missing return statement">}</error>

 Object f6(int i) throws Exception {
   if (true) return null;
 <error descr="Missing return statement">}</error>

 int f7(int i) {
  try {
   if (i==2) return 4;
   else throw new IllegalArgumentException();
  } catch (Exception e) {
  }
 <error descr="Missing return statement">}</error>

  int f8(int i) {
    try {
      //throw new Error();
    }
    finally {
      try {
        //throw new Exception();
      }
      catch (Exception e) {
        return 5;
      }
    }
 <error descr="Missing return statement">}</error>

 int cf1(int i) {
   return 0;
 }
 int cf2(int i) {
   if (i==2) return 0;
   else if (i==4) return -1;
   else return 2;
 }
 int cf3(int i) {
   return i==2 ? 3 : 5;
 }
 int cf4(int i) {
   switch (i) {
    case 1: return 4;
    case 3: return 6;
    default: return 5;
   }
 }
 int cf5(int i) {
   if (i>1) {
    if (i==4) return 0;
    else return i==3 ? 2 : 0;
   }
   else return 2;
 }
 int cf6(int i) {
   return cf4(i+1);
 }

 int cf7(int i) throws Exception {
   try {
     throw new Exception();
   } catch(Error e) {
     return 3;
   } finally {
     return 2;
   }

 }

 int cf8(int i) throws Exception {
   try {
     return 2;
   } finally {
     return 4;
   }
 }
 int cf9(int i) throws Exception {
   try {
     i = 5;
   } finally {
     throw new Exception();
   }
 }

 int cf10(int i) {

   while (true)
     return 0;
 }
 private static final int co = 1/2 + 1;
 int cf12(int i) {

   for (int k=0; (0xf0 | 0x0f) == 0xff && false != true && co == 1;k++)
     return 0;
 }


 int cf13() {
 try {
  try {
   throw new IllegalArgumentException();
   //throw new java.io.IOException();
  } catch (IllegalArgumentException e) {
   return 3;
  }
  finally {
   throw new java.io.IOException();
  }

 } catch (java.io.IOException ee) {
   return 88;
 }
 }

 int cf14() {
   try {
     cf13();
     return 13;
   } finally {
     cf13();
   }
 }
 
 int cf15() {
  try {
   int i=0;
   return i;
  } catch (Exception e) {
  } finally {
  }
  return 0;
 }
 int cf16() {
    try {
      if ( ! (1==3)) {
          return 0;
      }
    } finally {
        // Restore the current position of the other DynAny
    }
    return 1;
 }

 int cf17() {
        try {
            try {
                return 0;
            } finally {
            }
        } finally {
        }
  }
 int cf18(int i) {
   int k;
   try {
      if (i==4) return 0;
      k = 4;
   } finally {
   }
   return k;
  }


 void cf19() {

      try {

        try {
        }
        finally {
        }
          return ;
      }
      catch (Exception e) {
      }
 }

}

 class a5  {
interface ii {}



 int f1() throws Exception {
 <error descr="Missing return statement">}</error>
 
 Object f2(int i) throws Exception {
   if (i == 0) return null;
 <error descr="Missing return statement">}</error>

 Object f3(int i) throws Exception {
   while (i == 0) return null;
 <error descr="Missing return statement">}</error>

 Object f4(int i) throws Exception {
   switch (i) {
     case 1: return null;
   }
 <error descr="Missing return statement">}</error>

 Object f5(int i) throws Exception {
   if (i==2) return null;
   else if (i != 2) return null;
 <error descr="Missing return statement">}</error>

 Object f6(int i) throws Exception {
   if (true) return null;
 <error descr="Missing return statement">}</error>

 int f7(int i) {
  try {
   if (i==2) return 4;
   else throw new IllegalArgumentException();
  } catch (Exception e) {
  }
 <error descr="Missing return statement">}</error>

  int f8(int i) {
    try {
      //throw new Error();
    }
    finally {
      try {
        //throw new Exception();
      }
      catch (Exception e) {
        return 5;
      }
    }
 <error descr="Missing return statement">}</error>

  int f9(int i) {
    if (i==1) return 0;
    else assert false;
 <error descr="Missing return statement">}</error>



 int cf1(int i) {
   return 0;
 }
 int cf2(int i) {
   if (i==2) return 0;
   else if (i==4) return -1;
   else return 2;
 }
 int cf3(int i) {
   return i==2 ? 3 : 5;
 }
 int cf4(int i) {
   switch (i) {
    case 1: return 4;
    case 3: return 6;
    default: return 5;
   }
 }
 int cf5(int i) {
   if (i>1) {
    if (i==4) return 0;
    else return i==3 ? 2 : 0;
   }
   else return 2;
 }
 int cf6(int i) {
   return cf4(i+1);
 }

 int cf7(int i) throws Exception {
   try {
     throw new Exception();
   } catch(Error e) {
     return 3;
   } finally {
     return 2;
   }

 }

 int cf8(int i) throws Exception {
   try {
     return 2;
   } finally {
     return 4;
   }
 }
 int cf9(int i) throws Exception {
   try {
     i = 5;
   } finally {
     throw new Exception();
   }
 }

 int cf10(int i) {

   while (true)
     return 0;
 }
 private static final int co = 1/2 + 1;
 int cf12(int i) {

   for (int k=0; (0xf0 | 0x0f) == 0xff && false != true && co == 1;k++)
     return 0;
 }


 int cf13() {
 try {
  try {
   throw new IllegalArgumentException();
   //throw new java.io.IOException();
  } catch (IllegalArgumentException e) {
   return 3;
  }
  finally {
   throw new java.io.IOException();
  }

 } catch (java.io.IOException ee) {
   return 88;
 }
 }

 int cf14() {
   try {
     cf13();
     return 13;
   } finally {
     cf13();
   }
 }
 
 int cf15() {
  try {
   int i=0;
   return i;
  } catch (Exception e) {
  } finally {
  }
  return 0;
 }
 int cf16() {
    try {
      if ( ! (1==3)) {
          return 0;
      }
    } finally {
        // Restore the current position of the other DynAny
    }
    return 1;
 }

 int cf17() {
        try {
            try {
                return 0;
            } finally {
            }
        } finally {
        }
  }
 int cf18(int i) {
   int k;
   try {
      if (i==4) return 0;
      k = 4;
   } finally {
   }
   return k;
  }


 void cf19() {

      try {

        try {
        }
        finally {
        }
          return ;
      }
      catch (Exception e) {
      }
 }
 int cf20(boolean b1, boolean b2) {
    do {
    } while (b1 || b2);
    return 0;
 }

 public boolean cf21() throws IOException {
    try {
      return geta();
    }
    catch(IOException e) {
      throw new RuntimeException();
    }
    finally {
      geta();
    }
 }
 private boolean geta() throws IOException {
    return true;
 }

 String complexAss(Object o, Object p) {
   assert o != null && p != null;
   return null;
 }
}
