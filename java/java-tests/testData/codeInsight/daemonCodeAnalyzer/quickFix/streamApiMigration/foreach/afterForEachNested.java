// "Replace with forEach" "true"

import java.util.Arrays;

public class Test {
  public static void main(String[] args) {
    for (String module : args) {
      VirtualFile[] sourceRoots = foo(module);
        Arrays.stream(sourceRoots).forEach((VirtualFile sourceRoot) -> sourceRoot.substring());
    }
  }
}
