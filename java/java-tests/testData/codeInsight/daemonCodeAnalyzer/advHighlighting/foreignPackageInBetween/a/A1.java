package a;

import b.B;

class A1 {

  {
    new B().<error descr="'f()' is not public in 'a.A2'. Cannot be accessed from outside package">f</error>();
  }

}