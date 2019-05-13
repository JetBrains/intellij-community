import java.io.Serializable;

class X {

  <T extends Integer & Serializable> void foo(T param) {}

  {
    foo((Integer & Serializable)0);
  }
}
