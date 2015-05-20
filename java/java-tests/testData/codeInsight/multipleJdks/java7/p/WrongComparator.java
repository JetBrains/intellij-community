package p;
abstract class B extends A implements java.util.Comparator {
  {
    <error descr="Cannot resolve method 'reversed()'">reversed</error>();
  }
}