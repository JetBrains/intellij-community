import java.util.*;

interface A<T extends ArrayList<T>> {
  A<<error descr="Type parameter '? extends List<Object>' is not within its bound; should extend 'java.util.ArrayList<? extends java.util.List<java.lang.Object>>'">? extends List<Object></error>> foo();
  A<? extends List<T>> foo1();
}