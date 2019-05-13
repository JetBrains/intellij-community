
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  void f(Stream<ReviewDiffRecord> stream1) {
    final Supplier<List<String>> revisionDiffItemDTOs = () -> stream1.map(record -> {
      foo(null);
      final Object stream =  record.getAllNodes().stream();
      return "";
    }).collect(Collectors.toList());
  }


  private <T> void foo(T t) throws RuntimeException {}

  private class ReviewDiffRecord {
    List<String> getAllNodes() {
      return null;
    }
  }

}
