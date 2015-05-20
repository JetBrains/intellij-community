package p;
abstract class B extends A implements p.P {
  {
    final Class<? extends B> aClass = getClass();
  }
}
