import java.util.List;

/// @see List<<error descr="Cannot resolve symbol 'TotallyRealTypeThatExists'">TotallyRealTypeThatExists</error>>
///
/// [List<<error descr="Cannot resolve symbol 'TotallyRealTypeThatExists'">TotallyRealTypeThatExists</error>>]
class Test {
  void read(List<String> input) {}
}