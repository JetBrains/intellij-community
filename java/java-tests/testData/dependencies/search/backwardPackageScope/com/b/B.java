package com.b;
import com.a.*;
class B{
  A myA = new A();
  C myC = new C();
  void bb(){
    myA.aa();
    myC.cc();
  }
}