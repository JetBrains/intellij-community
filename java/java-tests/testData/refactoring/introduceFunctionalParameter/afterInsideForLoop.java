
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

class Test {

  public void run() {
    final Normalizer normalizer = new Normalizer();
    final List<String> input = new ArrayList<>();
    final List<String> output = new ArrayList<>();

    doNormalize(input, output, new Function<String,String>() {
        public String apply(String s) {
            return normalizer.normalize(s);
        }
    });
  }

  private void doNormalize(final List<String> input, final List<String> output, Function<String, String> anObject) {
    for (final String s : input) {
        output.add(anObject.apply(s));
    }
  }

  public static class Normalizer {
    public String normalize(final String s) {
      return s;
    }
  }
}