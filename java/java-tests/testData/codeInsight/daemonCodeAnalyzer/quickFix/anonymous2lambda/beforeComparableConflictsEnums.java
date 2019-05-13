// "Replace with lambda" "true"
public class MyNameConflict {

  {

    int x = 0;

    Comparable<E> c = new Compara<caret>ble<E>() {
      @Override
      public int compareTo(E x) {
        switch (x) {
          case EE:
            break;
        }
        return x.hashCode();
      }
    };
  }

  static enum E {
    EE;
  }

}
