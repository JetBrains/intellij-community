class Builder {
  Builder method(int p) { return this; }
    
  Builder b = 1 < n<caret>ew Builder().method( < x).method(2);
}
