class Builder {
    Builder method(int p) { return this; }
  
    Builder b = new Builder().<selection>method(<caret>1)</selection>.method(2);
}