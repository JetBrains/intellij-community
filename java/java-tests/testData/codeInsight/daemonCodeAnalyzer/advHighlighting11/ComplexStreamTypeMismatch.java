import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Demo {
  Map<Path, Long> fileSizes(List<File> files) {
    return files.stream().<error descr="Incompatible types. Found: 'java.util.Map<java.io.File,java.lang.Long>', required: 'java.util.Map<java.nio.file.Path,java.lang.Long>'">collect</error>(Collectors.toMap(f -> f, f -> f.length()));
  }

  void test() {
    Map<Long, List<String>> collect = Stream.of("xyz", "asfdasdfdasf", "dasfafasdfdf")
      .<error descr="Incompatible types. Found: 'java.util.Map<java.lang.Integer,java.util.List<java.lang.String>>', required: 'java.util.Map<java.lang.Long,java.util.List<java.lang.String>>'">collect</error>(Collectors.groupingBy(s -> s.length()));

    Map<String, Long> map = Stream.of("a", "b", "c").<error descr="Incompatible types. Found: 'java.util.Map<java.lang.String,java.lang.Integer>', required: 'java.util.Map<java.lang.String,java.lang.Long>'">collect</error>(Collectors.toMap(s -> s, s -> s.length()));
  }
}
