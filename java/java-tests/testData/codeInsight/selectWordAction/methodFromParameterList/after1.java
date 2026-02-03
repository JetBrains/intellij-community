class Builder {
    Builder method(int p) { return this; }
  
    Builder b = new Builder().method(<selection><caret>1</selection>).method(2);
}