import java.util.*;

class MyTClass {

  <T> void foo(final List<Object> objects){
    Collection<? extends T> collection = (Collection<? extends T>) objects;
  }
  
  <T> void foo1(final List<Object> objects){
    Collection<? super T> collection = (Collection<? super T>) objects;
  }

  <T extends String> void bar(final List<Object> objects){
    Collection<? extends T> collection = <error descr="Inconvertible types; cannot cast 'java.util.List<java.lang.Object>' to 'java.util.Collection<? extends T>'">(Collection<? extends T>) objects</error>;
  }
}