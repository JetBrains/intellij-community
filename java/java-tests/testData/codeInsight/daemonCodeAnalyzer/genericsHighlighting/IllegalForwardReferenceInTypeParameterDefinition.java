class A<T extends <warning descr="Forward reference may cause compilation error in some Javac versions (e.g. JDK 5 and JDK 6)">S</warning>, S> {
  T t;
}