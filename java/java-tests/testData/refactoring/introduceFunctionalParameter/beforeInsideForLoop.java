
import java.util.ArrayList;
import java.util.List;

class Test {

  public void run() {
    final Normalizer normalizer = new Normalizer();
    final List<String> input = new ArrayList<>();
    final List<String> output = new ArrayList<>();

    doNormalize(input, normalizer, output);
  }

  private void doNormalize(final List<String> input, final Normalizer normalizer, final List<String> output) {
    for (final String s : input) {
      output.add(<selection>normalizer.normalize(s)</selection>);
    }
  }

  public static class Normalizer {
    public String normalize(final String s) {
      return s;
    }
  }
}