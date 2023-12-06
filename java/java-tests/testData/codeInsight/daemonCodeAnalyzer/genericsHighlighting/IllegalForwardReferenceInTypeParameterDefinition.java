class A<T extends <warning descr="Forward references may cause compilation errors when using older javac versions (for example JDK 5 and JDK 6)">S</warning>, S> {
  T t;
}