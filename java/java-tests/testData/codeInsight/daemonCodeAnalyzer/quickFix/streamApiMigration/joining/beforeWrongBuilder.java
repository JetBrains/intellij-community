// "Replace with forEach" "true-preview"
class X {
  private void initBuilder() {
    StringBuilder builder = new StringBuilder();
    StringBuilder spaces = new StringBuilder();
    f<caret>or(int i = 0; i < 20; i++) {
      spaces.append("  ");
    }
    builder.append(spaces);
  }
}