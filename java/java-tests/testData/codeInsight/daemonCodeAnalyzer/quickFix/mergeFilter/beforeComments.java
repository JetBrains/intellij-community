// "Merge filter chain" "true"

import java.util.stream.Stream;
class Test {
    void foo(Stream<String> stringStream ) {
        stringStream.filt<caret>er(name -> name.startsWith("A"))//comment
              .filter(a -> a.//comment2
                      length() > 1 /*comment1*/).findAny();
    }
}
