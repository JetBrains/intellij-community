import java.util.List;

class Test {
  static void func1 (final List<? extends List<?>> list,
                     final List<? extends List<String>> listOfStrings){
    func2 <error descr="'func2(java.util.List<? extends java.util.List<T>>)' in 'Test' cannot be applied to '(java.util.List<capture<? extends java.util.List<?>>>)'">(list)</error>;
    func2 (listOfStrings);
  }
  static <T> void func2( List<? extends List<T>> list ){ }
}