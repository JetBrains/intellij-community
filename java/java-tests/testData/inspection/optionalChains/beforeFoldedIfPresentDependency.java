// "Remove folded 'ifPresent()' call" "false"
import java.util.Optional;

class Sample {
  public void foo(HexViewer hexViewer, Visitor visitor) {
    hexViewer.getDataModel().ifPresent<caret>(bytes ->
                                         hexViewer.getCaret().ifPresent(caret -> {
                                           final ByteWalker walker = new ByteWalker(bytes);
                                           walker.walk(visitor, caret.getSelectionStart(), caret.getSelectionEnd());
                                         })
    );
  }
  private class HexViewer {
    public Optional<String> getDataModel() {
      return null;
    }
    public Optional<Caret> getCaret() {
      return null;
    }
  }
  private class ByteWalker {
    public ByteWalker(Object bytes) {
    }

    public void walk(Visitor visitor, String start, String end) {

    }
  }
  private class Visitor {
  }

  private class Caret {
    public String getSelectionStart() {
      return "aaa";
    }
    public String getSelectionEnd() {
      return "bbb";
    }
  }
}