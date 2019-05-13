// "Replace with forEach" "true"

public class Test {
  public static void main(String[] args) {
    for (String module : args) {
      VirtualFile[] sourceRoots = foo(module);
      fo<caret>r (VirtualFile sourceRoot : sourceRoots) {
        sourceRoot.substring();
      }
    }
  }
}
