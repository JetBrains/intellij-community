import java.util.EventObject;

class DispatcherClass {
  boolean isDispatched(EventObject event) {}
  void <caret> dispatchEvent(EventObject event) {}
  void flushEvents() {}
  class Inner {
    void flush() {}
  }
  interface Dispatcher {
    boolean dispatch();
  }
  class Grand {
    class Parent {
      class Child {
        void dispatch() {}
      }
      class Inner {
        void flush() {}
      }
    }
    class Inner {
      void flush() {}
    }
  }
}