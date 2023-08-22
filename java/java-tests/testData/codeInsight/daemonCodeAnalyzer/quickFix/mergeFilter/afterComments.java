// "Merge filter chain" "true-preview"

import java.util.stream.Stream;
class Test {
    void foo(Stream<String> stringStream ) {
        stringStream.filter(name -> name.startsWith("A") && name.//comment2
                length() > 1//comment
                /*comment1*/
        ).findAny();
    }
}
