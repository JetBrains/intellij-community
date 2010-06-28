// fields double initialization
import java.io.*;
import java.net.*;
class Foo {
    final int k;
    final int ff = 5;
    Foo(int i) {
        <error descr="Variable 'k' might already have been assigned to">k</error> =1;
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
       <error descr="Variable 'k' might already have been assigned to">k</error> =1;
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
       <error descr="Variable 'k' might already have been assigned to">k</error> =1;
    }
}

class c4 {
    final int k;
    {
        k=0;
    }
    c4(int i) {
      if (false) 
        <error descr="Variable 'k' might already have been assigned to">k</error> =1;
    }
    c4() {
      this(0);
      <error descr="Variable 'k' might already have been assigned to">k</error> =1;
    }
}
// redirected ctrs
class c5 {
    <error descr="Variable 'k' might not have been initialized">final int k</error>;
    c5(int i) {
      k =1;
    }
    c5() {
      this(0);
      <error descr="Variable 'k' might already have been assigned to">k</error> =1;
    }


    c5(char c) {
    }
    c5(int i, int j) {
      this('c');
      k = 5;
    }
    c5(String s) {
      this(0,0);
      <error descr="Variable 'k' might already have been assigned to">k</error> =1;
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



// multiple initalizers
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
   <error descr="Variable 'y' might already have been assigned to">y</error> = ""+i;
 }
}
