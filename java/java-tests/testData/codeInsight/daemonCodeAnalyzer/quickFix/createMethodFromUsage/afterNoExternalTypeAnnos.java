// "Create method 'updatePublicWatchlist'" "true-preview"
import java.util.List;

class X {
  // IDEA-360115
  void test() {
    updatePublicWatchlist("name", List.of("/CL:XNYM", "/NG:XNYM", "/6E:XCME", "/GC:XCEC", "/YM:XCBT"));
  }

    private void updatePublicWatchlist(String name, List<String> strings) {
        
    }
}