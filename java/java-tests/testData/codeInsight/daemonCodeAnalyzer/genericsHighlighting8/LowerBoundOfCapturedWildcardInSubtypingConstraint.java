
import java.util.Collections;
import java.util.List;

interface Processor<P> {
  void process(P t);
}

class Test {
  void foo(Processor<? super List<String>> p) {
    p.process(Collections.emptyList());
  }

  void bar(Processor<? extends List<String>> p) {
    p.process<error descr="'process(capture<? extends java.util.List<java.lang.String>>)' in 'Processor' cannot be applied to '(java.util.List<java.lang.Object>)'">(Collections.emptyList())</error>;
  }
}