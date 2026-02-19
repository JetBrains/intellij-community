class A {
  void foo() {
<caret><selection>    nameWidth += tree.getFontMetrics(tree.getFont()/*.deriveFont(Font.ITALIC)*/).stringWidth(text);
</selection>
  }
}