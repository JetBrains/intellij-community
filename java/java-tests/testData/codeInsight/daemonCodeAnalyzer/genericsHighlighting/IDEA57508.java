
import java.util.List;

abstract class X {
  abstract <T> void copy(List<T> dest, List<? extends T> src);

  void foo(List<?> x, List<?> y){
    copy(x, <error descr="'copy(java.util.List<capture<?>>, java.util.List<? extends capture<?>>)' in 'X' cannot be applied to '(java.util.List<capture<?>>, java.util.List<capture<?>>)'">y</error>);
  }
}