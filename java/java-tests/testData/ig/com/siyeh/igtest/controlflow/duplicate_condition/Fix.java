class X {
  void foo(int x) {
    if (<warning descr="Duplicate condition 'Math.abs(x) > 0'">Math.abs(x) > 0</warning>) {
      if (<warning descr="Duplicate condition 'Math.abs(x) > 0'">Math<caret>.abs(x) > 0</warning>) {
        
      }
    }
  }
}