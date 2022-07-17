class Builder {
    Builder method(int p) { return this; }
  
    Builder b = <selection>new Builder().method(<caret>1)</selection>.method(2);
}