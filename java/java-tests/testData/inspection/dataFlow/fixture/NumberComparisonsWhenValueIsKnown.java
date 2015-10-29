class Test {
  int[] mIndex = null;
  int mSize = 0;

  void indexValues() {
    float loadFactor = mIndex == null ? 1.f : ((float) mSize) / ((float) mIndex.length);

    if (loadFactor < 0.25f || 0.75f <= loadFactor) {
      mIndex = new int[mSize * 2];
    }

    mIndex[0] = -1;
  }

  void bar() {
    int time = 99;
    String str = <warning descr="Condition 'time < 0' is always 'false'">time < 0</warning> ? "" : "";
    String str1 = <warning descr="Condition 'time <= 0' is always 'false'">time <= 0</warning> ? "" : "";
    String str2 = <warning descr="Condition 'time >= 100' is always 'false'">time >= 100</warning> ? "" : "";
  }
}