// "Adapt lambda return using 'toPath()'" "true-preview"
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Demo {
  Map<Path, Long> fileSizes(List<File> files) {
    return files.stream().<caret>collect(Collectors.toMap(f -> f, f -> f.length()));
  }
}
