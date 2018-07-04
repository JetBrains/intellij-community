// "Merge filter chain" "true"

import java.util.stream.Stream;
class Test {
    void foo(Stream<String> stringStream ) {
        stringStream.filter(name -> name.startsWith("A") && name.//comment2
                length() > 1//comment
                /*comment1*/
        ).findAny();
    }
}
