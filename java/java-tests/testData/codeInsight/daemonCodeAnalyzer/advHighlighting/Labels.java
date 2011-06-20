// labels
import java.io.*;
import java.net.*;

class a {
 void f() {
   <error descr="Label without statement">a:</error>
 }
 void f1() {
   a:
   <error descr="Label without statement">b:</error>
 }
 void f2() {
   a: return;
 }
 void f3() {
   a: return;
 }
 void f4() {
   a: 
   b:
    return;
 }
 void f5() {
   a: 
    if (4==5) return;
   b: ;
 }
 void f6() {
   a: ;
 }
} 	


class AlreadyInUse {
 void f0() {
   a: {
       f0();
       <error descr="Label 'a' already in use">a</error>: f0();
   }

 }
 void f1() {
   a:
   try {
     f1();
     <error descr="Label 'a' already in use">a</error>:
     f1();
   }
   finally {
   }
 }
 void f2() {
  {
    a:;
  }
  {
    a:;
  }
 }
 void f3() {
   a:
   new Object() {
     void f() {
      a:;
     }
   };
 }
}       