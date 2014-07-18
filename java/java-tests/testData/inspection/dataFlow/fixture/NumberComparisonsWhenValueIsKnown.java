import java.util.Arrays;

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
}