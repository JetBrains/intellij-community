import java.util.*;

class Foo {
  void m() {
    List l0 = new ArrayList();

    List l1 = new AbstractList() {
      @Override
      public int size() {
        return 0;
      }

      @Override
      public Object get(int index) {
        return null;
      }
    };
    List l2 = new AbstractList() {
      @Override
      public int size() {
        return 0;
      }

      @Override
      public Object get(int index) {
        return null;
      }
    };
    List l3 = new AbstractList() {
      @Override
      public int size() {
        return 0;
      }

      @Override
      public Object get(int index) {
        return null;
      }
    };

    <caret>
  }
}