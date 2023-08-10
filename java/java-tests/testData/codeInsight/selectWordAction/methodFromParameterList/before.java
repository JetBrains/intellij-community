class Builder {
    Builder method(int p) { return this; }
  
    Builder b = new Builder().method(<caret>1).method(2);
}