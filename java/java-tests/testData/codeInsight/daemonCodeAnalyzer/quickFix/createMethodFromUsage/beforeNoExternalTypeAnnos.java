// "Create method 'updatePublicWatchlist'" "true-preview"
import java.util.List;

class X {
  // IDEA-360115
  void test() {
    <caret>updatePublicWatchlist("name", List.of("/CL:XNYM", "/NG:XNYM", "/6E:XCME", "/GC:XCEC", "/YM:XCBT"));
  }
}