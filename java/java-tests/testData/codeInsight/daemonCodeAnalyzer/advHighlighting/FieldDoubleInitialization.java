// fields double initialization
import java.io.*;

class Foo {
    final int k;
    final int ff = 5;
    Foo(int i) {
        <error descr="Final field 'k' is already initialized in a class initializer">k</error> =1;
    }
    {
        k=0;
    }
}

class c2 {
    static final int k;
    static {
        k=0;
    }
    c2() {
      int i = k;
    }
    static {
       <error descr="Final field 'k' is already initialized in a class initializer">k</error> =1;
    }
}

class c3 {
    final int k;
    {
        k=0;
    }
    c3() {
      int i = k;
    }
    {
       <error descr="Final field 'k' is already initialized in a class initializer">k</error> =1;
    }
}

class c4 {
    final int k;
    {
        k=0;
    }
    c4(int i) {
      if (false)
        <error descr="Final field 'k' is already initialized in a class initializer">k</error> =1;
    }
    c4() {
      this(0);
      <error descr="Cannot assign final field 'k' after chained constructor call">k</error> =1;
    }
}
// redirected ctrs
class c5 {
    <error descr="Field 'k' might not have been initialized">final int k</error>;
    c5(int i) {
      k =1;
    }
    c5() {
      this(0);
      <error descr="Cannot assign final field 'k' after chained constructor call">k</error> =1;
    }


    c5(char c) {
    }
    c5(int i, int j) {
      this('c');
      <error descr="Cannot assign final field 'k' after chained constructor call">k</error> = 5;
    }
    c5(String s) {
      this(0,0);
      <error descr="Cannot assign final field 'k' after chained constructor call">k</error> =1;
    }
}

class c6 {
    final int i;
    c6() {
        this(0);
    }
    c6(int i) {
        this(0,0);
    }
    c6(int k, int l) {
        i = k;
    }
}



// multiple initializers
class c7 {
 private final String x;
 {
   x = "Hello";
 }

 private final String y;
 {
   y = x;
 }

 private static int i;
 {
   int j = 0;
 }

 static {
   i = 9;
 }

 {
   <error descr="Final field 'y' is already initialized in a class initializer">y</error> = ""+i;
 }
}
