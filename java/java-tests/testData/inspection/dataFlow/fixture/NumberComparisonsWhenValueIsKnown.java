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

  void forLoop() {
    int target = 3;
    for (int current = 0; <warning descr="Condition 'current < target' is always 'true'">current < target</warning>; ) {
      <warning descr="Variable is already assigned to this value">current</warning> = 0;
    }
  }

  void doLoop() {
    int current;
    int target = 3;
    do
    {
      current = 0;
    } while (<warning descr="Condition 'current < target' is always 'true'">current < target</warning>);
  }

  void whileLoop() {
    int current = 0;
    int target = 3;
    while (<warning descr="Condition 'current < target' is always 'true'">current < target</warning>)
    {
      <warning descr="Variable is already assigned to this value">current</warning> = 0;
    }
  }

}