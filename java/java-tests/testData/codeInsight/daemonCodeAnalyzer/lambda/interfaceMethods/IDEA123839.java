import java.util.Iterator;

interface A4 {
  default Iterator iterator() {
    return null;
  }
}

interface A5 {
  Iterator  iterator();
}

abstract class <error descr="B inherits abstract and default for iterator() from types A4 and A5">B</error> implements A5, A4 {}