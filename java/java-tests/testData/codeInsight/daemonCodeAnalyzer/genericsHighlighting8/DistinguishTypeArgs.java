import java.util.Set;

abstract class Test {
  public <T> Set<java.lang.Class<? extends T>> getSubTypesOf(Class<T> type){
    return null;
  }

  Set<Class<?>> foo(Class<?> interfaceClass){
    return  (Set<Class<?>>)getSubTypesOf(interfaceClass);
  }
}