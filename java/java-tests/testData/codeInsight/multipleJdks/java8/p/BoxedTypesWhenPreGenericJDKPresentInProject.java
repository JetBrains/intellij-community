import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

class Test {
  private static File findLastModified(final File[] files) {
    return Arrays.stream(files).sorted(Comparator.comparing(File::lastModified).reversed())
      .findFirst().orElse(null);
  }
}