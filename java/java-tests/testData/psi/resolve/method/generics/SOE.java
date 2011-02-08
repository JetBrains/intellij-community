import java.util.Comparator;

abstract class Z<T extends Z.NameRef> implements Comparator<T> {
   static abstract class NameRef implements java.lang.Comparable<T.<ref>NameRef> {
   }
}
