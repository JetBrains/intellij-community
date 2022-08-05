import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Demo {
  Map<Path, Long> fileSizes(List<File> files) {
    return <error descr="Incompatible types. Found: 'java.util.Map<java.io.File,java.lang.Long>', required: 'java.util.Map<java.nio.file.Path,java.lang.Long>'">files.stream().collect(Collectors.toMap(f -> f, f -> f.length()));</error>
  }

  void test() {
    Map<Long, List<String>> collect = <error descr="Incompatible types. Found: 'java.util.Map<java.lang.Integer,java.util.List<java.lang.String>>', required: 'java.util.Map<java.lang.Long,java.util.List<java.lang.String>>'">Stream.of("xyz", "asfdasdfdasf", "dasfafasdfdf")
      .collect(Collectors.groupingBy(s -> s.length()));</error>

    Map<String, Long> map = <error descr="Incompatible types. Found: 'java.util.Map<java.lang.String,java.lang.Integer>', required: 'java.util.Map<java.lang.String,java.lang.Long>'">Stream.of("a", "b", "c").collect(Collectors.toMap(s -> s, s -> s.length()));</error>
  }
}
