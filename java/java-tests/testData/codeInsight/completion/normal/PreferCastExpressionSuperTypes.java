public class Super {

    static void foo(Object o) {
      if (o instanceof SString) {
        ((S<caret>) o)
      }
    }

}

class SString implements java.io.Serializable, nonImported.SzNameInTheEnd {}