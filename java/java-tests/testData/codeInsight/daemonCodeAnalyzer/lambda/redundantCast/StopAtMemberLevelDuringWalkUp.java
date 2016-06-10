
import java.util.function.Function;

class It {

  Function<It, TextRange> f = new Function<It, TextRange>() {
    @Override
    public TextRange apply(final It it) {
      long offset = 9;
      return offset == 0 ? it.range() : it.range().shiftRight((int) offset);
    }
  };

  class TextRange {
    public TextRange shiftRight(final int offset) {
      return null;
    }
  }

  TextRange range() {
    return new TextRange();
  }

}